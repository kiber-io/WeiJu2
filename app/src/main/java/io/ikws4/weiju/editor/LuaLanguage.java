package io.ikws4.weiju.editor;

import android.content.res.AssetManager;
import android.os.Bundle;

import androidx.annotation.NonNull;

import org.eclipse.tm4e.core.theme.IRawTheme;
import org.eclipse.tm4e.languageconfiguration.internal.LanguageConfiguration;
import org.eclipse.tm4e.languageconfiguration.internal.supports.IndentationRule;

import java.io.IOException;
import java.io.InputStreamReader;

import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentLine;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.SymbolPairMatch;
import io.ikws4.weiju.util.Strings;

public class LuaLanguage extends EmptyLanguage {
    private TextMateLanguage mTextMateLanguage;
    private IndentationRule mIndentationRule;
    private final Editor mEditor;

    public LuaLanguage(Editor editor, IRawTheme theme) {
        mEditor = editor;

        try {
            AssetManager assets = editor.getContext().getAssets();
            String configuration = "textmate/lang/lua/language-configuration.json";
            String grammar = "textmate/lang/lua/tmLanguage.json";

            mTextMateLanguage = TextMateLanguage.create(grammar, assets.open(grammar), new InputStreamReader(assets.open(configuration)), theme);
            mIndentationRule = LanguageConfiguration.load(new InputStreamReader(assets.open(configuration))).getIndentationRule();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getIndentAdvance(@NonNull ContentReference content, int line, int column) {
        return getIndentAdvance(content.getLine(line).substring(0, column));
    }

    public int getIndentAdvance(String line) {
        if (line.matches(mIndentationRule.getIncreaseIndentPattern())) {
            return getTabSize();
        }
        return 0;
    }

    public void updateTheme(IRawTheme theme) {
        mTextMateLanguage.updateTheme(theme);
    }

    public void setTabSize(int tabSize) {
        mTextMateLanguage.setTabSize(tabSize);
    }

    public int getTabSize() {
        return mTextMateLanguage.getTabSize();
    }

    public boolean isAutoCompleteEnabled() {
        return mTextMateLanguage.isAutoCompleteEnabled();
    }

    public void setAutoCompleteEnabled(boolean autoCompleteEnabled) {
        mTextMateLanguage.setAutoCompleteEnabled(autoCompleteEnabled);
    }

    private final NewlineHandler[] mNewlineHandlers = new NewlineHandler[]{new EndwiseNewlineHandler()};

    @Override
    public NewlineHandler[] getNewlineHandlers() {
        return mNewlineHandlers;
    }

    @Override
    public SymbolPairMatch getSymbolPairs() {
        return mTextMateLanguage.getSymbolPairs();
    }

    @Override
    @NonNull
    public AnalyzeManager getAnalyzeManager() {
        return mTextMateLanguage.getAnalyzeManager();
    }

    @Override
    public void requireAutoComplete(@NonNull ContentReference content, @NonNull CharPosition position, @NonNull CompletionPublisher publisher, @NonNull Bundle extraArguments) {
        mTextMateLanguage.requireAutoComplete(content, position, publisher, extraArguments);
    }

    class EndwiseNewlineHandler implements NewlineHandler {

        @Override
        public boolean matchesRequirement(String beforeText, String afterText) {
            return beforeText.matches(mIndentationRule.getIncreaseIndentPattern());
        }

        private final StringBuilder mStringBuilder = new StringBuilder();

        @Override
        public NewlineHandleResult handleNewline(String beforeText, String afterText, int tabSize) {
            String leadingSpaces = Strings.repeat(" ", Strings.leadingSpaceCount(beforeText));
            int indent = getIndentAdvance(beforeText);

            boolean shouldAddEnd = false;
            int nextLine = mEditor.getCursor().getLeftLine() + 1;

            int lines = mEditor.getText().getLineCount();
            if (nextLine <= lines) {
                if (nextLine == mEditor.getText().getLineCount()) {
                    shouldAddEnd = true;
                } else {
                    ContentLine line;
                    do {
                        line = mEditor.getText().getLine(nextLine++);
                    } while (nextLine < lines && Strings.isOnlySpaces(line));

                    if (!line.toString().startsWith(leadingSpaces + "end")) {
                        shouldAddEnd = true;
                    }
                }
            }
            mStringBuilder.setLength(0);
            mStringBuilder.append("\n");
            mStringBuilder.append(Strings.repeat(" ", indent + leadingSpaces.length()));
            int leftShift = 0;
            if (shouldAddEnd) {
                mStringBuilder.append("\n").append(leadingSpaces).append("end");
                leftShift = leadingSpaces.length() + 4;
            }

            return new NewlineHandleResult(mStringBuilder.toString(), leftShift);
        }
    }
}