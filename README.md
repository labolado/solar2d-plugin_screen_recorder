## solar2d_plugin_screen_recorder
Implement video recording and share functionality on both *iOS* and *Android* platforms. For Android development, refer to the [HBRecorder](https://github.com/HBiSoft/HBRecorder) library.

### How to use:
Add following to your *build.settings* to use:
``` Lua
{
    plugins = {
        ["plugin.screenRecorder"] = {
            publisherId = "com.labolado",
            supportedPlatforms = {
                ["android"] = {url = "https://github.com/labolado/solar2d-plugin_screen_recorder/releases/download/v7/2020.3620-android.tgz"},
                ["iphone"] = {url = "https://github.com/labolado/solar2d-plugin_screen_recorder/releases/download/v7/2020.3620-iphone.tgz"},
                ["iphone-sim"] = {url = "https://github.com/labolado/solar2d-plugin_screen_recorder/releases/download/v7/2020.3620-iphone-sim.tgz"},
            }
        }
    },
}

```
Usage:
``` Lua
local SR = require("plugin.screenRecorder")

local function onStart(e)
    if e.isError then
        print(e.errorMessage)
    else
        -- do something
    end
end
-- to start recording screen
SR.start(onStart)

-- ...
-- to stop recording screen
SR.stop()
```
