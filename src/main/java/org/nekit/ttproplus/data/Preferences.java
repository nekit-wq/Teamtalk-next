/*
 * Copyright (c) 2005-2018, BearWare.dk
 * 
 * Contact Information:
 *
 * Bjoern D. Rasmussen
 * Kirketoften 5
 * DK-8260 Viby J
 * Denmark
 * Email: contact@bearware.dk
 * Phone: +45 20 20 54 59
 * Web: http://www.bearware.dk
 *
 * This source code is part of the TeamTalk SDK owned by
 * BearWare.dk. Use of this file, or its compiled unit, requires a
 * TeamTalk SDK License Key issued by BearWare.dk.
 *
 * The TeamTalk SDK License Agreement along with its Terms and
 * Conditions are outlined in the file License.txt included with the
 * TeamTalk SDK distribution.
 *
 */

package org.nekit.ttproplus.data;

public class Preferences {

    //duplicates of pref_connection.xml (isn't there an easier way to do this??)
    public static final String 
            PREF_GENERAL_NICKNAME = "nickname_text",
            PREF_GENERAL_STATUSMSG = "statusmsg_text",
            PREF_GENERAL_SHOWUSERNAMES = "showusernames_checkbox",
            PREF_GENERAL_OFFICIALSERVERS = "showofficialservers_checkbox",
            PREF_GENERAL_UNOFFICIALSERVERS = "showunofficialservers_checkbox",
            PREF_GENERAL_BEARWARE_CHECKED = "bearwareid_checkbox",
            PREF_GENERAL_BEARWARE_USERNAME = "bearware_username",
            PREF_GENERAL_BEARWARE_TOKEN = "bearware_token",
            PREF_GENERAL_GENDER = "gender_checkbox",
            PREF_GENERAL_CLIENTNAME = "clientname_text",
            PREF_GENERAL_LANGUAGE = "app_language",
            PREF_GENERAL_DEFAULT_FILE_MANAGER = "default_file_manager";
    public static final String
            PREF_SOUNDSYSTEM_MEDIAFILE_VOLUME = "mediafilevolume_seekbar",
            PREF_SOUNDSYSTEM_VOICEACTIVATION_LEVEL = "voice_activation_level",
            PREF_SOUNDSYSTEM_MASTERVOLUME = "mastervolume",
            PREF_SOUNDSYSTEM_MICROPHONEGAIN = "microphonegain",
            PREF_SOUNDSYSTEM_MUTE_ON_TRANSMISSION = "mute_speakers_on_tx_checkbox",
            PREF_SOUNDSYSTEM_SPEAKERPHONE = "speakerphone_checkbox",
            PREF_SOUNDSYSTEM_BLUETOOTH_HEADSET = "bluetooth_headset_checkbox",
            PREF_SOUNDSYSTEM_VOICEPROCESSING = "voiceprocessing_checkbox",
            PREF_SOUNDSYSTEM_INPUT_SOURCE = "audio_input_source";
    public static final String
            PREF_JOIN_ROOT_CHAN = "auto_join_root_checkbox",
            PREF_CONNECTION_SEAMLESS_RECONNECT = "seamless_reconnect",
            PREF_SUB_TEXTMESSAGE = "sub_txtmsg_checkbox",
            PREF_SUB_CHANMESSAGE = "sub_chanmsg_checkbox",
            PREF_SUB_BCAST_MESSAGES = "sub_bcastmsg_checkbox",
            PREF_SUB_VOICE = "sub_voice_checkbox",
            PREF_SUB_VIDCAP = "sub_video_checkbox",
            PREF_SUB_DESKTOP = "sub_desktop_checkbox",
            PREF_SUB_MEDIAFILE = "sub_mediafile_checkbox";

    public static final String
            PREF_ANTISPAM_ENABLED = "antispam_enabled",
            PREF_ANTISPAM_MSG_LIMIT = "antispam_msg_limit",
            PREF_ANTISPAM_UNSUB_ALL = "antispam_unsub_all";

    public static final String
            PREF_BG_MGMT_ENABLED = "bg_mgmt_enabled",
            PREF_BG_MGMT_SHOW_VOICE = "bg_mgmt_show_voice",
            PREF_BG_MGMT_SHOW_MUTE = "bg_mgmt_show_mute",
            PREF_BG_MGMT_SHOW_PING = "bg_mgmt_show_ping",
            PREF_BG_MGMT_SHOW_CHAT = "bg_mgmt_show_chat",
            PREF_BG_MGMT_SHOW_CHANNELS = "bg_mgmt_show_channels",
            PREF_BG_MGMT_SHOW_SERVERS = "bg_mgmt_show_servers",
            PREF_BG_MGMT_DISPLAY_TYPE = "bg_mgmt_display_type",
            PREF_BG_MGMT_CLOSE_ON_APP_OPEN = "bg_mgmt_close_on_app_open";

    public static final String
            PREF_RECORDING_FORMAT = "recording_format",
            PREF_RECORDING_MP3_BITRATE = "recording_mp3_bitrate",
            PREF_RECORDING_PATH = "recording_path",
            PREF_RECORDING_FOLDER_STRUCTURE = "recording_folder_structure",
            PREF_RECORDING_SESSION_MODE = "recording_session_mode",
            PREF_RECORDING_SILENCE_PAUSE = "recording_silence_pause",
            PREF_RECORDING_MULTITRACK = "recording_multitrack",
            PREF_RECORDING_SHOW_DIALOG = "recording_show_dialog_after",
            PREF_RECORDING_SHOW_TOAST = "recording_show_toast_notifications",
            PREF_RECORDING_AUTO = "auto_record_conversations";

    public static final String
            PREF_DISPLAY_SHOW_MIC_ACTIVITY = "display_show_mic_activity",
            PREF_DISPLAY_SHOW_PING_NO_SERVER = "display_show_ping_no_server",
            PREF_DISPLAY_SHOW_ROOT_USERS = "display_show_root_users",
            PREF_DISPLAY_SHOW_ROOT_SERVER_BACK_BTN = "display_show_root_server_back_btn",
            PREF_DISPLAY_SHOW_USER_QUICK_ACTIONS = "display_show_user_quick_actions",
            PREF_DISPLAY_QUICK_KICK_SRV = "display_quick_kick_srv",
            PREF_DISPLAY_QUICK_BAN_SRV = "display_quick_ban_srv",
            PREF_DISPLAY_QUICK_KICK_CHAN = "display_quick_kick_chan",
            PREF_DISPLAY_QUICK_BAN_CHAN = "display_quick_ban_chan";

    public static final String PREF_WELCOME_SHOWN = "welcome_shown";
    public static final String PREF_IGNORED_UPDATE_VERSION = "ignored_update_version";
}
