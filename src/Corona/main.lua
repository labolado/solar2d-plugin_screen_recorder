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
