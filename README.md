In-App Video Recording + Video Sharing Plugin (Android & iOS) for Solar2d Game Engine.

Implement video recording and share functionality on both *iOS* and *Android* platforms. For Android development, refer to the [HBRecorder](https://github.com/HBiSoft/HBRecorder) library.

### How to use:
Add following to *build.settings*:
``` Lua
{
    plugins = {
        ["plugin.screenRecorder"] = {
            publisherId = "com.labolado"
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
