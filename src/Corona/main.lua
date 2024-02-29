-- local library = require "plugin.library"

-- -- This event is dispatched to the global Runtime object
-- -- by `didLoadMain:` in MyCoronaDelegate.mm
-- local function delegateListener( event )
-- 	native.showAlert(
-- 		"Event dispatched from `didLoadMain:`",
-- 		"of type: " .. tostring( event.name ),
-- 		{ "OK" } )
-- end
-- Runtime:addEventListener( "delegate", delegateListener )

-- -- This event is dispatched to the following Lua function
-- -- by PluginLibrary::show() in PluginLibrary.mm
-- local function listener( event )
-- 	print( "Received event from Library plugin (" .. event.name .. "): ", event.message )
-- end

-- library.init( listener )

-- timer.performWithDelay( 1000, function()
-- 	library.show( "corona" )
-- end )

---[[
local topInset, leftInset, bottomInset, rightInset = display.getSafeAreaInsets()
bottomInset = topInset
local maxInset = math.max(leftInset, rightInset)
leftInset = maxInset
rightInset = maxInset

_W      = display.contentWidth
_H      = display.contentHeight
_AW     = display.actualContentWidth - (leftInset + rightInset)
_AH     = display.actualContentHeight - (topInset + bottomInset)
_T      = display.screenOriginY + topInset
_B      = _T + _AH
_L      = display.screenOriginX + leftInset
_R      = _L + _AW
_RW     = _R - _L
_RH     = _B - _T
_CX     = display.contentCenterX
_CY     = display.contentCenterY

local function safeRequire(modname)
    local ok, mod = pcall(require, modname)
    if ok then
        return mod
    end
end

local R = safeRequire("plugin.screenRecorder")

local rect = display.newRect(_CX, _CY, _RW, _RH)
timer.performWithDelay(1000, function()
	rect.fill.r = math.random()
	rect.fill.g = math.random()
	rect.fill.b = math.random()
end, -1)

local txt
local source
local function logOnScreen(...)
	local args = {...}
	for i=1, #args do
	    args[i] = tostring(args[i])
	end
	local text = table.concat(args, " ")
	if txt == nil then
		txt = display.newText(text, _CX, _T + 90, native.systemFont, 50)
	else
		txt.text = text
		txt.isVisible = true
	end
	txt:setFillColor(1, 0, 1, 1)
	if source then
		timer.cancel(source)
	end
	source = timer.performWithDelay(4000, function()
		-- txt:removeSelf()
		txt.isVisible = false
	end)
end

local function createButton(options)
	local startBttn = display.newGroup()
	if options.parent then
		options.parent:insert(startBttn)
	end
	local base = display.newRect(startBttn, 0, 0, 200, 200)
	base:setFillColor(0, 0, 0, 1)
	local text = display.newText{
		parent = startBttn,
		text = options.text,
		fontSize = 60,
	}
	text:setFillColor(1, 1, 1, 1)
	startBttn:translate(options.x, options.y)

	startBttn:addEventListener("touch", function(e)
		if e.phase == "ended" then
			if options.onReleased then options.onReleased() end
		end
	end)
	return startBttn
end

createButton({
	text = "start",
	x = _CX - 300,
	y = _CY,
	onReleased = function()
		R.start()
		logOnScreen("start")
	end
})

createButton({
	text = "stop",
	x = _CX + 300,
	y = _CY,
	onReleased = function()
		R.stop()
		logOnScreen("stop")
	end
})

createButton({
	text = "capture",
	x = _CX - 300,
	y = _CY + 600,
	onReleased = function()
		logOnScreen("capture")

		local view = display.capture(display.currentStage, { saveToPhotoLibrary=true, captureOffscreenArea=false })
		view:removeSelf()
	end
})

createButton({
	text = "show",
	x = _CX + 300,
	y = _CY + 600,
	onReleased = function()
		logOnScreen("show")
		R.show()
	end
})
--]]

-- local replayKit = require( "plugin.replayKit" )

-- replayKit.setBroadcastingOptions(true) --enable mic for live stream
-- local toggle = 0
-- local myText
-- local function listener( e )
-- 	local json = require("json")
-- 	print(json.encode(e))
-- end

-- local function handleTap( event )
-- 	if (event.phase == "began") then
-- 		print("Can record?")
-- 		print(replayKit.availableToRecord())
-- 		print("Is recording?")
-- 		print(replayKit.recordingScreen())
-- 		print("Is using mic?")
-- 		print(replayKit.usingMicrophoneForRecoding())
-- 		print(replayKit.getBroadcastingUrl())
-- 		if (toggle == 0) then--for broadcasting
-- 			replayKit.startBroadcasting(listener)
-- 			event.target.text = "Stop Broadcasting"
-- 			toggle = 1
-- 		else
-- 			replayKit.stopBroadcasting(listener)
-- 			event.target.text = "Broadcast"
-- 			toggle = 0
-- 		end
-- 		--[[
-- 		-- this enables recording
-- 		if (toggle == 0) then--for broadcasting
-- 			replayKit.record(listener, true) -- lis and mic
-- 			event.target.text = "Stop Recording"
-- 			toggle = 1
-- 		else
-- 			replayKit.stopRecording(listener)
-- 			event.target.text = "Record"
-- 			toggle = 0
-- 		end
-- 		]]--
-- 		return true
-- 	end
-- end
-- local function handleTap2( event )
-- 	if (event.phase == "began") then
-- 		print("Are we broadcasting?")
-- 		print(replayKit.broadcasting())
-- 		print("Broadcast is paused?")
-- 		print(replayKit.broadcastIsPaused())
-- 		print("what is my url for my broadcast?")
-- 		print(replayKit.getBroadcastingUrl())
-- 		if (toggle == 0) then--for broadcasting
-- 			replayKit.record(listener, true) -- lis and mic
-- 			event.target.text = "Stop Recording"
-- 			toggle = 1
-- 		else
-- 			replayKit.stopRecording(listener)
-- 			event.target.text = "Record"
-- 			toggle = 0
-- 		end
-- 		--[[
-- 		-- this enables recording

-- 		]]--
-- 		return true
-- 	end
-- end
-- myText = display.newText("Record", display.contentCenterX, display.contentCenterY, native.systemFont, 20)
-- myText:addEventListener( "touch", handleTap2 )

-- myText2 = display.newText("Broadcast", display.contentCenterX, display.contentCenterY+ 30, native.systemFont, 20)
-- myText2:addEventListener( "touch", handleTap )
-- -- this is the sample code from corona sdk DragMe

-- --this is sample I got from corona sample code
-- local arguments =
-- {
-- 	{ x=100, y=60, w=100, h=100, r=10, red=1, green=0, blue=0 },
-- 	{ x=60, y=100, w=100, h=100, r=10, red=0, green=1, blue=0 },
-- 	{ x=140, y=140, w=100, h=100, r=10, red=0, green=0, blue=1 }
-- }

-- local function getFormattedPressure( pressure )
-- 	if pressure then
-- 		return math.floor( pressure * 1000 + 0.5 ) / 1000
-- 	end
-- 	return "unsupported"
-- end

-- local function printTouch( event )
--  	if event.target then
--  		local bounds = event.target.contentBounds
--  		print( "event(" .. event.phase .. ") ("..event.x..","..event.y..") bounds: "..bounds.xMin..","..bounds.yMin..","..bounds.xMax..","..bounds.yMax.."; pressure: "..getFormattedPressure(event.pressure) )
-- 	end
-- end

-- local function onTouch( event )
-- 	local t = event.target

-- 	-- Print info about the event. For actual production code, you should
-- 	-- not call this function because it wastes CPU resources.
-- 	printTouch(event)

-- 	local phase = event.phase
-- 	if "began" == phase then
-- 		-- Make target the top-most object
-- 		local parent = t.parent
-- 		parent:insert( t )
-- 		display.getCurrentStage():setFocus( t )

-- 		-- Spurious events can be sent to the target, e.g. the user presses
-- 		-- elsewhere on the screen and then moves the finger over the target.
-- 		-- To prevent this, we add this flag. Only when it's true will "move"
-- 		-- events be sent to the target.
-- 		t.isFocus = true

-- 		-- Store initial position
-- 		t.x0 = event.x - t.x
-- 		t.y0 = event.y - t.y
-- 	elseif t.isFocus then
-- 		if "moved" == phase then
-- 			-- Make object move (we subtract t.x0,t.y0 so that moves are
-- 			-- relative to initial grab point, rather than object "snapping").
-- 			t.x = event.x - t.x0
-- 			t.y = event.y - t.y0

-- 			-- Gradually show the shape's stroke depending on how much pressure is applied.
-- 			if ( event.pressure ) then
-- 				t:setStrokeColor( 1, 1, 1, event.pressure )
-- 			end
-- 		elseif "ended" == phase or "cancelled" == phase then
-- 			display.getCurrentStage():setFocus( nil )
-- 			t:setStrokeColor( 1, 1, 1, 0 )
-- 			t.isFocus = false
-- 		end
-- 	end

-- 	-- Important to return true. This tells the system that the event
-- 	-- should not be propagated to listeners of any objects underneath.
-- 	return true
-- end

-- -- Iterate through arguments array and create rounded rects (vector objects) for each item
-- for _,item in ipairs( arguments ) do
-- 	local button = display.newRoundedRect( item.x, item.y, item.w, item.h, item.r )
-- 	button:setFillColor( item.red, item.green, item.blue )
-- 	button.strokeWidth = 6
-- 	button:setStrokeColor( 1, 1, 1, 0 )

-- 	-- Make the button instance respond to touch events
-- 	button:addEventListener( "touch", onTouch )
-- end

-- -- listener used by Runtime object. This gets called if no other display object
-- -- intercepts the event.
-- local function printTouch2( event )
-- 	print( "event(" .. event.phase .. ") ("..event.x..","..event.y..") ("..getFormattedPressure(event.pressure)..")" )
-- end

-- Runtime:addEventListener( "touch", printTouch2 )
