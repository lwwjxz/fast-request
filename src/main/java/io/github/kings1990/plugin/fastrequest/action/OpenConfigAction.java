package io.github.kings1990.plugin.fastrequest.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import icons.PluginIcons;
import io.github.kings1990.plugin.fastrequest.util.MyResourceBundleUtil;
import org.jetbrains.annotations.NotNull;

public class OpenConfigAction extends AnAction {

    public OpenConfigAction() {
        super(MyResourceBundleUtil.getKey("ManageConfig"), MyResourceBundleUtil.getKey("ManageConfig"), PluginIcons.ICON_CONFIG);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getData(LangDataKeys.PROJECT);
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "Restful Fast Request");
    }
}
