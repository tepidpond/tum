@echo off
PATH=%PATH%;"C:\Program Files (x86)\Git\bin"
cd eclipse\.metadata\.plugins
git update-index --assume-unchanged org.eclipse.core.resources/.projects/Minecraft/.location
git update-index --assume-unchanged org.eclipse.core.resources/.root/0.tree
git update-index --assume-unchanged org.eclipse.core.runtime/.settings/org.eclipse.core.resources.prefs
git update-index --assume-unchanged org.eclipse.core.runtime/.settings/org.eclipse.debug.ui.prefs
git update-index --assume-unchanged org.eclipse.core.runtime/.settings/org.eclipse.epp.usagedata.gathering.prefs
git update-index --assume-unchanged org.eclipse.core.runtime/.settings/org.eclipse.jdt.core.prefs
git update-index --assume-unchanged org.eclipse.core.runtime/.settings/org.eclipse.ui.editors.prefs
git update-index --assume-unchanged org.eclipse.core.runtime/.settings/org.eclipse.ui.ide.prefs
git update-index --assume-unchanged org.eclipse.core.runtime/.settings/org.eclipse.ui.prefs
git update-index --assume-unchanged org.eclipse.debug.core/.launches/Client.launch
git update-index --assume-unchanged org.eclipse.debug.core/.launches/Server.launch
git update-index --assume-unchanged org.eclipse.debug.ui/launchConfigurationHistory.xml
cd ..\..\..
gradlew.bat setupDecompWorkspace eclipse
