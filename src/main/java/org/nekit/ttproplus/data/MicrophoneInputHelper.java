package org.nekit.ttproplus.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MicrophoneInfo;
import android.os.Build;
import android.text.TextUtils;
import dk.bearware.SoundDevice;
import dk.bearware.TeamTalkBase;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import org.nekit.ttproplus.R;

public final class MicrophoneInputHelper {
    public static final int CAPTURE_MODE_DEFAULT = 0;
    public static final int CAPTURE_MODE_MONO = 1;
    public static final int CAPTURE_MODE_STEREO = 2;

    public static final int EXPERIMENTAL_CAPTURE_DISABLED = 0;
    public static final int EXPERIMENTAL_CAPTURE_MONO = 1;
    public static final int EXPERIMENTAL_CAPTURE_STEREO = 2;

    public static final String EXPERIMENTAL_INPUT_DEVICE_DEFAULT = "default";

    public static final class InputDeviceOption {
        public final String deviceId;
        public final String summary;
        public final String title;

        public InputDeviceOption(String id, String title, String summary) {
            this.deviceId = id;
            this.title = title;
            this.summary = summary;
        }
    }

    public static final class InputModeOption {
        public final int deviceId;
        public final String summary;
        public final String title;

        public InputModeOption(int id, String title, String summary) {
            this.deviceId = id;
            this.title = title;
            this.summary = summary;
        }
    }

    private MicrophoneInputHelper() {
    }

    public static String buildDetectedMicrophonesReport(Context context) {
        if (Build.VERSION.SDK_INT < 28) {
            return context.getString(R.string.pref_summary_detected_microphones_unsupported);
        }
        List<MicrophoneInfo> microphones = getMicrophones(context);
        StringBuilder sb = new StringBuilder();
        sb.append(context.getString(R.string.pref_microphone_detected_report_intro));
        sb.append("\n\n");
        sb.append(context.getString(R.string.pref_microphone_detected_report_limit));
        if (microphones.isEmpty()) {
            sb.append("\n\n");
            sb.append(context.getString(R.string.pref_summary_detected_microphones_none));
            return sb.toString();
        }
        int i = 0;
        while (i < microphones.size()) {
            MicrophoneInfo micInfo = microphones.get(i);
            sb.append("\n\n");
            i++;
            sb.append(i).append(". ");
            String description = micInfo.getDescription();
            if (TextUtils.isEmpty(description)) {
                description = context.getString(R.string.pref_microphone_unknown_name, micInfo.getId());
            }
            sb.append(description).append("\n");
            sb.append(context.getString(R.string.pref_microphone_report_type, getTypeLabel(context, micInfo.getType()))).append("\n");
            sb.append(context.getString(R.string.pref_microphone_report_location, getLocationLabel(context, micInfo.getLocation()))).append("\n");
            sb.append(context.getString(R.string.pref_microphone_report_group, formatGroup(micInfo.getGroup(), micInfo.getIndexInTheGroup()))).append("\n");
            sb.append(context.getString(R.string.pref_microphone_report_directionality, getDirectionalityLabel(context, micInfo.getDirectionality())));
        }
        return sb.toString();
    }

    private static String formatCleanDeviceSummary(Context context, AudioDeviceInfo audioDeviceInfo) {
        int type = audioDeviceInfo.getType();
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
            return context.getString(R.string.pref_microphone_device_wired_summary);
        }
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return context.getString(R.string.pref_microphone_device_bluetooth_summary);
        }
        if (type == AudioDeviceInfo.TYPE_USB_DEVICE || type == AudioDeviceInfo.TYPE_USB_HEADSET) {
            return context.getString(R.string.pref_microphone_device_usb_summary);
        }
        if (type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
            return context.getString(R.string.pref_microphone_device_builtin_summary);
        }
        if (Build.VERSION.SDK_INT >= 31 && type == 26) {
            return context.getString(R.string.pref_microphone_device_bluetooth_summary);
        }
        return getTypeLabel(context, type);
    }

    private static String formatCleanDeviceTitle(Context context, AudioDeviceInfo audioDeviceInfo) {
        int type = audioDeviceInfo.getType();
        CharSequence productName = audioDeviceInfo.getProductName();
        String trim = productName != null ? productName.toString().trim() : "";
        String model = Build.MODEL != null ? Build.MODEL.trim() : "";
        if (type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
            return context.getString(R.string.pref_microphone_device_builtin_title);
        }
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
            return context.getString(R.string.pref_microphone_device_wired_title);
        }
        if (type == AudioDeviceInfo.TYPE_USB_DEVICE || type == AudioDeviceInfo.TYPE_USB_HEADSET) {
            return (TextUtils.isEmpty(trim) || trim.equalsIgnoreCase(model)) ? context.getString(R.string.pref_microphone_device_usb_title) : trim;
        }
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || (Build.VERSION.SDK_INT >= 31 && type == 26)) {
            return (TextUtils.isEmpty(trim) || trim.equalsIgnoreCase(model)) ? context.getString(R.string.pref_microphone_device_bluetooth_title) : trim;
        }
        return (TextUtils.isEmpty(trim) || trim.equalsIgnoreCase(model)) ? getTypeLabel(context, type) : trim;
    }

    private static String formatGroup(int group, int index) {
        String g = group == -1 ? "?" : String.valueOf(group);
        String idx = index == -1 ? "?" : String.valueOf(index);
        return g + " / " + idx;
    }

    public static List<InputDeviceOption> getAvailableExperimentalInputDevices(Context context) {
        ArrayList<InputDeviceOption> list = new ArrayList<>();
        list.add(new InputDeviceOption(EXPERIMENTAL_INPUT_DEVICE_DEFAULT, context.getString(R.string.pref_experimental_input_device_default_title), context.getString(R.string.pref_experimental_input_device_default_summary)));
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            if (devices != null) {
                for (AudioDeviceInfo dev : devices) {
                    if (dev != null && isValidUserSelectableInput(dev)) {
                        list.add(new InputDeviceOption(String.valueOf(dev.getId()), formatCleanDeviceTitle(context, dev), formatCleanDeviceSummary(context, dev)));
                    }
                }
            }
        }
        return list;
    }

    public static List<InputModeOption> getAvailableInputModes(Context context) {
        return getExperimentalCaptureModes(context);
    }

    public static String getDetectedMicrophonesSummary(Context context) {
        if (Build.VERSION.SDK_INT < 28) {
            return context.getString(R.string.pref_summary_detected_microphones_unsupported);
        }
        List<MicrophoneInfo> microphones = getMicrophones(context);
        return microphones.isEmpty() ? context.getString(R.string.pref_summary_detected_microphones_none) : context.getString(R.string.pref_summary_detected_microphones_count, microphones.size());
    }

    private static String getDirectionalityLabel(Context context, int dir) {
        switch (dir) {
            case MicrophoneInfo.DIRECTIONALITY_OMNI:
                return context.getString(R.string.pref_microphone_directionality_omni);
            case MicrophoneInfo.DIRECTIONALITY_BI_DIRECTIONAL:
                return context.getString(R.string.pref_microphone_directionality_bidirectional);
            case MicrophoneInfo.DIRECTIONALITY_CARDIOID:
                return context.getString(R.string.pref_microphone_directionality_cardioid);
            case MicrophoneInfo.DIRECTIONALITY_HYPER_CARDIOID:
                return context.getString(R.string.pref_microphone_directionality_hypercardioid);
            case MicrophoneInfo.DIRECTIONALITY_SUPER_CARDIOID:
                return context.getString(R.string.pref_microphone_directionality_supercardioid);
            default:
                return context.getString(R.string.pref_microphone_directionality_unknown);
        }
    }

    public static int getExperimentalCaptureMode(SharedPreferences prefs) {
        if (prefs == null) {
            return 0;
        }
        int mode = 0;
        try {
            mode = prefs.getInt(Preferences.PREF_SOUNDSYSTEM_EXPERIMENTAL_CAPTURE_MODE, 0);
        } catch (ClassCastException e) {
            try {
                mode = Integer.parseInt(prefs.getString(Preferences.PREF_SOUNDSYSTEM_EXPERIMENTAL_CAPTURE_MODE, "0"));
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
        if (mode == 1 || mode == 2) {
            return mode;
        }
        return 0;
    }

    public static String getExperimentalCaptureModeLabel(Context context, int mode) {
        if (mode == 1) {
            return context.getString(R.string.pref_experimental_capture_mode_mono_title);
        }
        if (mode == 2) {
            return context.getString(R.string.pref_experimental_capture_mode_stereo_title);
        }
        return context.getString(R.string.pref_experimental_capture_mode_disabled_title);
    }

    public static List<InputModeOption> getExperimentalCaptureModes(Context context) {
        ArrayList<InputModeOption> list = new ArrayList<>();
        list.add(new InputModeOption(0, context.getString(R.string.pref_experimental_capture_mode_disabled_title), context.getString(R.string.pref_experimental_capture_mode_disabled_summary)));
        list.add(new InputModeOption(1, context.getString(R.string.pref_experimental_capture_mode_mono_title), context.getString(R.string.pref_experimental_capture_mode_mono_summary)));
        list.add(new InputModeOption(2, context.getString(R.string.pref_experimental_capture_mode_stereo_title), context.getString(R.string.pref_experimental_capture_mode_stereo_summary)));
        return list;
    }

    public static String getExperimentalInputDeviceId(SharedPreferences prefs) {
        if (prefs == null) {
            return EXPERIMENTAL_INPUT_DEVICE_DEFAULT;
        }
        try {
            String devId = prefs.getString(Preferences.PREF_SOUNDSYSTEM_EXPERIMENTAL_INPUT_DEVICE, EXPERIMENTAL_INPUT_DEVICE_DEFAULT);
            return TextUtils.isEmpty(devId) ? EXPERIMENTAL_INPUT_DEVICE_DEFAULT : devId;
        } catch (ClassCastException e) {
            try {
                int devIdInt = prefs.getInt(Preferences.PREF_SOUNDSYSTEM_EXPERIMENTAL_INPUT_DEVICE, -1);
                return devIdInt != -1 ? String.valueOf(devIdInt) : EXPERIMENTAL_INPUT_DEVICE_DEFAULT;
            } catch (Exception ignored) {
                return EXPERIMENTAL_INPUT_DEVICE_DEFAULT;
            }
        } catch (Exception ignored) {
            return EXPERIMENTAL_INPUT_DEVICE_DEFAULT;
        }
    }

    public static String getExperimentalInputDeviceLabel(Context context, SharedPreferences prefs) {
        String targetId = getExperimentalInputDeviceId(prefs);
        for (InputDeviceOption opt : getAvailableExperimentalInputDevices(context)) {
            if (TextUtils.equals(opt.deviceId, targetId)) {
                return opt.title;
            }
        }
        return context.getString(R.string.pref_experimental_input_device_default_title);
    }

    private static String getLocationLabel(Context context, int loc) {
        switch (loc) {
            case MicrophoneInfo.LOCATION_MAINBODY:
                return context.getString(R.string.pref_microphone_location_mainbody);
            case MicrophoneInfo.LOCATION_MAINBODY_MOVABLE:
                return context.getString(R.string.pref_microphone_location_movable);
            case MicrophoneInfo.LOCATION_PERIPHERAL:
                return context.getString(R.string.pref_microphone_location_peripheral);
            default:
                return context.getString(R.string.pref_microphone_location_unknown);
        }
    }

    private static List<MicrophoneInfo> getMicrophones(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null || Build.VERSION.SDK_INT < 28) {
            return Collections.emptyList();
        }
        try {
            return audioManager.getMicrophones();
        } catch (IOException | SecurityException | UnsupportedOperationException ignored) {
            return Collections.emptyList();
        }
    }

    private static String getTypeLabel(Context context, int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                return context.getString(R.string.pref_microphone_type_builtin);
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return context.getString(R.string.pref_microphone_type_wired_headset);
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return context.getString(R.string.pref_microphone_type_wired_headphones);
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return context.getString(R.string.pref_microphone_type_bluetooth_sco);
            case AudioDeviceInfo.TYPE_USB_DEVICE:
                return context.getString(R.string.pref_microphone_type_usb_device);
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return context.getString(R.string.pref_microphone_type_usb_headset);
            case 26: // TYPE_BLE_HEADSET
                return context.getString(R.string.pref_microphone_type_bluetooth_le);
            default:
                return context.getString(R.string.pref_microphone_type_unknown, type);
        }
    }

    public static boolean isExperimentalCaptureEnabled(SharedPreferences prefs) {
        return getExperimentalCaptureMode(prefs) != 0;
    }

    private static boolean isValidUserSelectableInput(AudioDeviceInfo dev) {
        int type = dev.getType();
        if (type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return true;
        }
        return Build.VERSION.SDK_INT >= 31 && type == 26;
    }

    public static boolean isVoiceCommunicationModeAvailable() {
        Vector<SoundDevice> vector = new Vector<>();
        if (!TeamTalkBase.getSoundDevices(vector)) {
            return false;
        }
        for (SoundDevice dev : vector) {
            if (dev.nDeviceID == 1 && dev.nMaxInputChannels > 0) {
                return true;
            }
        }
        return false;
    }
}
