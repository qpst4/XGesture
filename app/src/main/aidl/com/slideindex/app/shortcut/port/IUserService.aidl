package com.slideindex.app.shortcut.port;

interface IUserService {
    String runShellCommand(String command) = 1;
}
