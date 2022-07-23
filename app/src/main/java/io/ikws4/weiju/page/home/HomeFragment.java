package io.ikws4.weiju.page.home;

import android.content.ClipData;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Pair;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import io.ikws4.weiju.R;
import io.ikws4.weiju.page.editor.EditorFragment;
import io.ikws4.weiju.page.home.view.AppListView;
import io.ikws4.weiju.page.home.view.ScriptListView;
import io.ikws4.weiju.widget.searchbar.SearchBar;
import io.ikws4.weiju.widget.searchbar.SelectedAppInfoItemLoader;

public class HomeFragment extends Fragment implements MenuProvider {
    // For xposed to hook this variable to indicate
    // that xposed works.
    private static boolean XPOSED_ENABLED = false;

    public HomeFragment() {
        super(R.layout.home_fragment);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        HomeViewModel vm = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        AppListView vApps = view.findViewById(R.id.rv_item_list);
        ScriptListView vScripts = view.findViewById(R.id.rv_scripts);
        ViewFlipper vfScripts = view.findViewById(R.id.vf_scripts);

        vScripts.registerCallbacks(new ScriptListView.Callbacks() {
            @Override
            public void onAddToMyScripts(View v, ScriptListView.ScriptItem item) {
                vm.removeFromAvaliableScripts(item);
                vm.addToMyScript(item);
            }

            @Override
            public void onRemoveFromMyScripts(View v, ScriptListView.ScriptItem item) {
                vm.removeFromMyScripts(item);
            }

            @Override
            public void onGotoEditorFragment(ScriptListView.ScriptItem item) {
                Bundle bundle = new Bundle();
                bundle.putParcelable("item", item);

                requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .setCustomAnimations(
                        R.anim.slide_in_bottom,
                        R.anim.slide_out_top,
                        R.anim.slide_in_bottom,
                        R.anim.slide_out_top
                    )
                    .add(R.id.fragment_container, EditorFragment.class, bundle)
                    .addToBackStack("home")
                    .commit();
            }
        });

        SearchBar searchBar = new SearchBar(getContext(), new SelectedAppInfoItemLoader());
        searchBar.setOnItemClickListener(item -> {
            AppListView.AppItem app = (AppListView.AppItem) item.userData;
            vm.switchApp(app);
            vm.addApp(app);
            return true;
        });

        vApps.registerCallbacks(new AppListView.Callbacks() {

            @Override
            public void onRequireSwitchApp(AppListView.AppItem app) {
                vm.switchApp(app);
            }

            @Override
            public void onRequireAddApp() {
                searchBar.show();
            }

            @Override
            public void onRequireRemoveApp(View v, float x, float y, int index, AppListView.AppItem item) {
                View.DragShadowBuilder shadow = new View.DragShadowBuilder(v) {
                    @Override
                    public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
                        super.onProvideShadowMetrics(outShadowSize, outShadowTouchPoint);
                        outShadowTouchPoint.set((int) x, (int) y);
                    }
                };
                v.startDragAndDrop(ClipData.newPlainText("", ""), shadow, new Pair<>(index, item), 0);
            }
        });

        vfScripts.setOnDragListener((v, event) -> {
            var item = (Pair) event.getLocalState();
            int index = (int) item.first;
            AppListView.AppItem app = (AppListView.AppItem) item.second;
            ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    vm.removeApp(app);
                    actionBar.setSubtitle("Drag to the right to delete.");
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    actionBar.setSubtitle("Are you sure you want to delete it?");
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    actionBar.setSubtitle("Drag to the right to delete.");
                    return true;
                case DragEvent.ACTION_DROP:
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    if (event.getResult() == false) {
                        vm.addApp(index, app);
                    } else {
                        vm.updateSelectedAppAfterRemove(index, app);
                    }
                    actionBar.setSubtitle(null);
                    return true;
            }
            return false;
        });

        vm.getSelectedApps().observe(getViewLifecycleOwner(), infos -> {
            if (infos.isEmpty()) {
                vfScripts.setDisplayedChild(0);
            } else {
                vApps.setData(infos);
                vApps.scrollToSelectedPkgPosition();
                vfScripts.setDisplayedChild(1);
            }
        });

        vm.getAvaliableScripts().observe(getViewLifecycleOwner(), scripts -> {
            vScripts.setData(vm.getMyScripts().getValue(), scripts);
        });

        vm.getMyScripts().observe(getViewLifecycleOwner(), scripts -> {
            vScripts.setData(scripts, vm.getAvaliableScripts().getValue());
        });
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menu.clear();

        menuInflater.inflate(R.menu.home_menu, menu);
        if (XPOSED_ENABLED) {
            menu.findItem(R.id.xposed_status).setVisible(false);
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.xposed_status) {
            Toast.makeText(getContext(), "WeiJu was not enabled in xposed.", Toast.LENGTH_SHORT).show();
        } else if (item.getItemId() == R.id.settings) {
            Toast.makeText(getContext(), "TODO: Settings", Toast.LENGTH_SHORT).show();
        } else {
            return false;
        }
        return true;
    }
}
