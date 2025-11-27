-- Version: 7.2 (Essential fixes: Android encoder init, iOS build settings & Lua events)
local metadata =
{
	plugin =
	{
		format = 'staticLibrary',
		staticLibs = { 'plugin_screenRecorder', },
		frameworks = {},
		frameworksOptional = {},
		-- usesSwift = true,
	},
}

return metadata
