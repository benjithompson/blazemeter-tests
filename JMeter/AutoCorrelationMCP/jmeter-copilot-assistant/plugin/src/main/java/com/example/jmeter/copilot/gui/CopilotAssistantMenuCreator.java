package com.example.jmeter.copilot.gui;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.apache.jmeter.gui.plugin.MenuCreator;

public class CopilotAssistantMenuCreator implements MenuCreator {

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MenuCreator.MENU_LOCATION location) {
        if (location != MenuCreator.MENU_LOCATION.TOOLS) {
            return new JMenuItem[0];
        }

        JMenuItem item = new JMenuItem("Copilot Assistant...");
        item.addActionListener(e -> CopilotAssistantDialog.showDialog());
        return new JMenuItem[]{item};
    }

    @Override
    public JMenu[] getTopLevelMenus() {
        return new JMenu[0];
    }

    @Override
    public boolean localeChanged(javax.swing.MenuElement menuElement) {
        return false;
    }

    @Override
    public void localeChanged() {
        // no-op
    }
}
