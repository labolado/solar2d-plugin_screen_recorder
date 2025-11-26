-- Version: 7.0 (Stability improvements)
local metadata =
{
	plugin =
	{
		format = 'aar',
		manifest =
		{
			permissions = {},
			usesPermissions =
			{
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.WRITE_INTERNAL_STORAGE",
                "android.permission.RECORD_AUDIO",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
                "android.permission.FOREGROUND_SERVICE_MICROPHONE"
			},
			usesFeatures = {},
			applicationChildElements =
			{
                [[
                    <service android:name="com.hbisoft.hbrecorder.ScreenRecordService"
                        android:foregroundServiceType="mediaProjection|microphone"
                        tools:targetApi="q" />

                    <receiver android:name="com.hbisoft.hbrecorder.NotificationReceiver"/>
                ]]

			},
		},
	},
	coronaManifest = {
		dependencies = {
		

		},
	},
}

return metadata
