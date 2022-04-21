import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.Win32VK;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class KeyboardHook {
    public KeyboardHook() {}
    private static volatile boolean quit;

    private static WinUser.HHOOK hhk;

    private static final WinUser.INPUT[] inputs = (WinUser.INPUT[]) new WinUser.INPUT().toArray(4);
    private static final int LLKHF_EXTENDED_RELEASED = 0b10000001;
    private static final int LLKHF_EXTENDED = 0b00000001;
    private static final int LLKHF_ALTDOWN = 0b00100000;

    static class Wrapper {
        List<Map.Entry<Integer, Integer>> pressedKeys = new ArrayList<>();
        final User32 lib =  User32.INSTANCE;
    }

    private static boolean lockInstance(final String lockFile) {
        try {
            final File file = new File(lockFile);
            final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
            final FileLock fileLock = randomAccessFile.getChannel().tryLock();
            if (fileLock != null) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        fileLock.release();
                        randomAccessFile.close();
                    } catch (Exception e) {
                        System.err.println("Unable to remove lock file: " + lockFile + e.getMessage());
                    }
                }));
                return true;
            }
        } catch (Exception e) {
            System.err.println("Unable to create and/or lock file: " + lockFile + e.getMessage());
        }
        return false;
    }

    public static void main(String[] args) {
        if (args.length == 0 || !lockInstance(args[0])) {
            System.exit(0);
        }
        for (int i = 0; i < inputs.length; i++) {
            inputs[i].type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
            inputs[i].input.setType("ki");
            inputs[i].input.ki.wScan = new WinDef.WORD(0);
            inputs[i].input.ki.time = new WinDef.DWORD(0);
            inputs[i].input.ki.dwExtraInfo = new BaseTSD.ULONG_PTR(0);
            inputs[i].input.ki.dwFlags = new WinDef.DWORD(i < 2 ? 0 : 2);
            inputs[i].input.ki.wVk = new WinDef.WORD(i % 2 == 0 ? Win32VK.VK_LMENU.code : Win32VK.VK_LSHIFT.code);
        }
        Wrapper wrapper = new Wrapper();
        WinUser.LowLevelKeyboardProc keyboardHook = (nCode, wParam, info) -> {
            if (nCode >= 0) {
                wrapper.pressedKeys.add(new AbstractMap.SimpleEntry<>(info.vkCode, info.flags));
                switch (wParam.intValue()) {
                    case WinUser.WM_SYSKEYDOWN:
                    case WinUser.WM_KEYDOWN:
                        System.out.println("WM_KEYDOWN (info.vkCode, info.flags) = " + info.vkCode + " " + Integer.toBinaryString(info.flags));
                        System.out.println("pressedKeys.size (down) = " + wrapper.pressedKeys.size());
                        while (wrapper.pressedKeys.size() > 4) {
                            wrapper.pressedKeys.remove(0);
                        }
                        if (info.vkCode == Win32VK.VK_Q.code) {
                            if ((wrapper.lib.GetAsyncKeyState(Win32VK.VK_RCONTROL.code) & 0x8000) != 0 &&
                                    (wrapper.lib.GetAsyncKeyState(Win32VK.VK_RSHIFT.code) & 0x8000) != 0) {
                                quit = true;
                            }
                        }
                        break;
                    case WinUser.WM_SYSKEYUP:
                    case WinUser.WM_KEYUP:
                        System.out.println("WM_KEYUP (info.vkCode, info.flags) = " + info.vkCode + " " + Integer.toBinaryString(info.flags));
                        System.out.println("pressedKeys.size (up) = " + wrapper.pressedKeys.size());
                        if (wrapper.pressedKeys.size() >= 4) {
                            boolean RCtrl_down = wrapper.pressedKeys.get(wrapper.pressedKeys.size() - 4).getKey() == Win32VK.VK_RCONTROL.code
                                    && ((wrapper.pressedKeys.get(wrapper.pressedKeys.size() - 4).getValue() & LLKHF_EXTENDED) == LLKHF_EXTENDED);
                            boolean RShift_down = wrapper.pressedKeys.get(wrapper.pressedKeys.size() - 3).getKey() == Win32VK.VK_RSHIFT.code
                                    && ((wrapper.pressedKeys.get(wrapper.pressedKeys.size() - 3).getValue() & LLKHF_EXTENDED) == LLKHF_EXTENDED);
                            boolean RCtrl_up = wrapper.pressedKeys.get(wrapper.pressedKeys.size() - 2).getKey() == Win32VK.VK_RCONTROL.code
                                    && ((wrapper.pressedKeys.get(wrapper.pressedKeys.size() - 2).getValue() & LLKHF_EXTENDED_RELEASED) == LLKHF_EXTENDED_RELEASED);
                            boolean RShift_up = wrapper.pressedKeys.get(wrapper.pressedKeys.size() - 1).getKey() == Win32VK.VK_RSHIFT.code
                                    && ((wrapper.pressedKeys.get(wrapper.pressedKeys.size() - 1).getValue() & LLKHF_EXTENDED_RELEASED) == LLKHF_EXTENDED_RELEASED);
                            if (RCtrl_down && RShift_down && RCtrl_up && RShift_up) {
                                wrapper.pressedKeys.clear();
                                wrapper.lib.SendInput(new WinDef.DWORD(inputs.length), inputs, inputs[0].size());
                                System.out.println("Language changed!");
                            }
                        }
                        break;
                }
            }
            Pointer ptr = info.getPointer();
            long peer = Pointer.nativeValue(ptr);
            return wrapper.lib.CallNextHookEx(hhk, nCode, wParam, new WinDef.LPARAM(peer));
        };

        WinDef.HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);

        hhk =  wrapper.lib.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, keyboardHook, hMod, 0);

//        System.out.println(new Win32Exception(Kernel32.INSTANCE.GetLastError()).getMessage());
        final var executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
           if(quit) {
               System.out.println("unhook and exit");
               wrapper.lib.UnhookWindowsHookEx(hhk);
               System.exit(0);
           }
        }, 0, 500, TimeUnit.MILLISECONDS);

//         This bit never returns from GetMessage
        int result;
        WinUser.MSG msg = new WinUser.MSG();
        while ((result = wrapper.lib.GetMessage(msg, null, 0, 0)) != 0) {
            if (result == -1) {
                System.err.println("error in get message");
                break;
            }
            else {
                System.out.println("got message");
                wrapper.lib.TranslateMessage(msg);
                wrapper.lib.DispatchMessage(msg);
            }
        }
        wrapper.lib.UnhookWindowsHookEx(hhk);
    }
}