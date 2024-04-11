package io.ikws4.weiju.page.editor;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.reflect.TypeToken;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import io.github.rosemoe.sora.text.CharPosition;
import io.ikws4.weiju.R;
import io.ikws4.weiju.api.API;
import io.ikws4.weiju.api.openai.ChatResponse;
import io.ikws4.weiju.editor.Editor;
import io.ikws4.weiju.events.StartChatEvent;
import io.ikws4.weiju.exceptions.ScriptSaveException;
import io.ikws4.weiju.page.BaseFragment;
import io.ikws4.weiju.page.MainViewModel;
import io.ikws4.weiju.page.editor.view.EditorSymbolBar;
import io.ikws4.weiju.page.home.HomeViewModel;
import io.ikws4.weiju.page.home.widget.ScriptListView;
import io.ikws4.weiju.storage.Preferences;
import io.ikws4.weiju.util.Logger;
import io.ikws4.weiju.utils.FileUtility;
import io.ikws4.weiju.utils.GsonUtility;
import io.ikws4.weiju.utils.IOnBackPressed;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class EditorFragment extends BaseFragment implements IOnBackPressed {
    private Editor vEditor;
    private HomeViewModel vm;
    private MainViewModel mainVM;
    private ScriptListView.ScriptItem mItem;
    private CompositeDisposable mCompositeDisposable;
    private List<ChatResponse.Message> mChatMessages;
    private Disposable mChatDisposable;
    private CharPosition mCursorStart;
    private boolean mCloseWithoutSaving = false;

    public EditorFragment() {
        super(R.layout.editor_fragment);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        mCompositeDisposable = new CompositeDisposable();
        mChatMessages = new ArrayList<>();
        setupRoleForChatGPT();

        mItem = requireArguments().getParcelable("item");
        // getSupportActionBar().setSubtitle(mItem.id);

        vEditor = view.findViewById(R.id.editor);
        EditorSymbolBar vSymbolBar = view.findViewById(R.id.editor_symbol_bar);

        vEditor.setText(mItem.script);
        if (mItem.isPackage) {
            vEditor.setEditable(false);
            vSymbolBar.setVisibility(View.GONE);
        }
        vSymbolBar.attach(vEditor);
        configEditor();

        mainVM = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        // Auto Save
//        if (!mItem.isPackage) {
//            mCompositeDisposable.add(Observable.timer(15, TimeUnit.SECONDS)
//                .repeat()
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe((it) -> {
//                    mainVM.showProgressBar();
//                    try {
//                        trySave();
//                    } catch (ScriptSaveException e) {
//                        Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
//                    }
//                    mCompositeDisposable.add(Completable.timer(2, TimeUnit.SECONDS)
//                        .subscribe(mainVM::hideProgressBar));
//                }));
//        }
    }

    private void configEditor() {
        var prefs = Preferences.getInstance(getContext());
        vEditor.setWordwrap(prefs.get(Preferences.EDITOR_WORD_WRAP, false));

        vEditor.getEditorActionWindow().addButton(R.drawable.ic_chatgpt, (v) -> {
            EventBus.getDefault().post(new StartChatEvent());
        });
    }

    private void showSaveErrorDialog(String error) {
        Activity activity = requireActivity();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.Dialog_WeiJu)
                .setTitle(R.string.script_contains_error)
                .setCancelable(false)
                .setMessage(error)
                .setPositiveButton(R.string.cancel, null)
                .setNegativeButton("Don't save", (dialogInterface, i) -> {
                    mCloseWithoutSaving = true;
                    activity.onBackPressed();
                });
        builder.getBackground().setTint(activity.getColor(R.color.base));
        builder.show();
    }

    @Override
    public boolean onBackPressed() {
        if (mCloseWithoutSaving) return false;
        try {
            trySave();
            return false;
        } catch (ScriptSaveException e) {
            showSaveErrorDialog(e.getMessage());
            return true;
        }
    }

    class Response {
        public List<ChatResponse.Message> messages;
    }

    private void setupRoleForChatGPT() {
        var json = FileUtility.readAssetFile(getContext(), "chatgpt_as_weiju2_script_assistant.txt");
        mChatMessages.addAll(GsonUtility.fromJson(json, TypeToken.get(Response.class)).messages);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        super.onCreateMenu(menu, menuInflater);

        menuInflater.inflate(R.menu.editor_menu, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.help) {
            Toast.makeText(getContext(), "TODO: Help", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.close) {
            try {
                trySave();
                requireActivity().onBackPressed();
            } catch (ScriptSaveException e) {
                showSaveErrorDialog(e.getMessage());
            }
        } else if (id == R.id.undo) {
            vEditor.undo();
        } else if (id == R.id.redo) {
            vEditor.redo();
        } else if (id == R.id.stop_chatgpt_streaming) {
            if (!mChatDisposable.isDisposed()) {
                mChatDisposable.dispose();
                resetChat();
            }
        } else {
            return false;
        }
        return true;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mCompositeDisposable.clear();
        mainVM.hideProgressBar();
//        try {
//            trySave();
//        } catch (ScriptSaveException e) {
//            Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
//        }
    }

    private boolean trySave() throws ScriptSaveException {
        // Don't need perform save becuase it can not be change
        if (mItem.isPackage) return true;

        var item = ScriptListView.ScriptItem.from(vEditor.getText().toString());
        if (item.equals(mItem)) return true;

        if (hasSameMetadataInMyScripts(item)) {
            throw new ScriptSaveException("Same metadata already exist");
        } else if (hasMetadataError(item)) {
            throw new ScriptSaveException("Metadata parse error");
        } else {
            String msg = item.verify();
            if (!msg.isEmpty()) {
                throw new ScriptSaveException(msg);
            }
        }
        vm.replaceInMyScripts(mItem, item);
        mItem = item;
        return true;
    }

    private boolean hasMetadataError(ScriptListView.ScriptItem item) {
        return item == ScriptListView.ScriptItem.EMPTY_ITEM;
    }

    private boolean hasSameMetadataInMyScripts(ScriptListView.ScriptItem item) {
        for (var it : vm.getMyScripts().getValue()) {
            if (it.idEquals(mItem)) continue;
            if (it.idEquals(item)) return true;
        }
        return false;
    }

    @Override
    public String getFragmentTitle() {
        return getString(R.string.app_name);
    }

    @Override
    public String getFragmentSubtitle() {
        return mItem.id;
    }

    @Override
    public boolean isDisplayHomeAsUp() {
        return false;
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStartChatEvent(StartChatEvent event) {
        var cursor = vEditor.getCursor();
        // Toast.makeText(getContext(), "Receiving...", Toast.LENGTH_SHORT).show();

        String content = "";
        if (cursor.isSelected()) {
            content = vEditor.getText().substring(cursor.getLeft(), cursor.getRight());
        } else if (cursor.getLeftLine() > 0) {
            var startLine = cursor.getLeftLine() - 1;
            var text = vEditor.getText();
            while (startLine >= 0 && text.getLineString(startLine).isEmpty()) {
                startLine--;
            }
            while (startLine >= 0 && text.getLineString(startLine).startsWith("--")) {
                startLine--;
            }
            content = vEditor.getText().substring(
                cursor.getIndexer().getCharIndex(startLine + 1, 0),
                cursor.right().index
            );
        }
        content = content.trim();

        if (!content.isEmpty()) {
            mChatMessages.add(new ChatResponse.Message("user", content));

            mCursorStart = cursor.right();
            getMainActivity().showProgressBar();
            mCompositeDisposable.add(mChatDisposable = API.OpenAIApi.chat(
                    Preferences.getInstance(getContext()).get(Preferences.OPENAI_API_KEY, ""),
                    Preferences.getInstance(getContext()).get(Preferences.OPENAI_CHAT_MODEL, ""),
                    mChatMessages
                ).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    response -> {
                        if (vEditor.isEnabled()) {

                            if (cursor.getRightLine() + 1 >= vEditor.getText().getLineCount()) {
                                vEditor.getText().insert(mCursorStart.line, mCursorStart.column, "\n");
                            }

                            if (!vEditor.getText().getLineString(mCursorStart.line).isEmpty()) {
                                mCursorStart.line += 1;
                            }
                            vEditor.setSelection(mCursorStart.line, 0);
                            vEditor.setEnabled(false);

                            getMenu().findItem(R.id.close).setVisible(false);
                            getMenu().findItem(R.id.stop_chatgpt_streaming).setVisible(true);
                        }

                        var delta = response.choices.get(0).delta;
                        if (delta.content == null) return;
                        vEditor.insertText(delta.content, delta.content.length());
                    },
                    err -> {
                        Logger.e(err);
                        Toast.makeText(getContext(), err.getMessage(), Toast.LENGTH_LONG).show();
                        resetChat();
                    }, () -> {
                        resetChat();
                    }));
        } else {
            Toast.makeText(getContext(), "Please select the area you want to ask for", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetChat() {
        var cursor = vEditor.getCursor();
        vEditor.setEnabled(true);
        vEditor.setSelectionRegion(mCursorStart.line, 0, cursor.getRightLine(), cursor.getRightColumn());
        mChatMessages.remove(mChatMessages.size() - 1);
        getMainActivity().hideProgressBar();

        getMenu().findItem(R.id.close).setVisible(true);
        getMenu().findItem(R.id.stop_chatgpt_streaming).setVisible(false);
    }
}
