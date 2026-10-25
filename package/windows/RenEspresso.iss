; RenEspresso POS - Inno Setup Installer Script
; Produces a standalone, professional Windows Installer for RenEspresso POS

#define MyAppName "RenEspresso POS"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "RenEspresso"
#define MyAppURL "http://localhost/Renesspresso/"
#define MyAppExeName "RenEspresso.exe"

[Setup]
; NOTE: The value of AppId uniquely identifies this application.
AppId={{9F573618-9322-4B1C-B180-2E3D21E53A90}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\RenEspresso
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=..\..\target\dist\installer
OutputBaseFilename=RenEspresso_v1.0_Setup
SetupIconFile=RenEspresso.ico
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "..\..\target\dist\RenEspresso\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
; Optional sample db.properties for quick client network setup
Source: "..\..\src\main\resources\db.properties.sample"; DestDir: "{app}"; DestName: "db.properties.sample"; Flags: ignoreversion
Source: "RenEspresso.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\RenEspresso.ico"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\RenEspresso.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
