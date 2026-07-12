#ifndef StagingDir
  #error StagingDir is required
#endif
#ifndef BuildId
  #error BuildId is required
#endif
#ifndef DistDir
  #error DistDir is required
#endif

[Setup]
AppId={{89D650B6-3C70-4EAA-A768-4C2937C3E16B}
AppName=Forge DIY
AppVersion={#BuildId}
AppVerName=Forge DIY {#BuildId}
DefaultDirName={localappdata}\Programs\ForgeDIY
DefaultGroupName=Forge DIY
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir={#DistDir}
OutputBaseFilename=ForgeDIY-{#BuildId}-Setup
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
SetupIconFile={#StagingDir}\res\icons\forge.ico
UninstallDisplayIcon={app}\forge.exe
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=yes

[Languages]
Name: "ChineseSimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "快捷方式："; Flags: checkedonce

[Files]
Source: "{#StagingDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{userprograms}\Forge DIY"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\launch_forge_diy.ps1"""; WorkingDir: "{app}"; IconFilename: "{app}\forge.exe"
Name: "{userdesktop}\Forge DIY"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\launch_forge_diy.ps1"""; WorkingDir: "{app}"; IconFilename: "{app}\forge.exe"; Tasks: desktopicon

[Run]
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\install_diy_payload.ps1"" -Quiet"; WorkingDir: "{app}"; Flags: runhidden waituntilterminated
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\launch_forge_diy.ps1"""; WorkingDir: "{app}"; Description: "启动 Forge DIY"; Flags: nowait postinstall skipifsilent
