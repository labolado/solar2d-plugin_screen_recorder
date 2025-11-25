//
//  PluginLibrary.mm
//  TemplateApp
//
//  Copyright (c) 2012 __MyCompanyName__. All rights reserved.
//

#import "PluginScreenRecorder.h"
#import "PreviewViewController.h"

#include <CoronaRuntime.h>
#import <UIKit/UIKit.h>
#import <ReplayKit/RPScreenRecorder.h>

// ----------------------------------------------------------------------------

class PluginScreenRecorder
{
	public:
		typedef PluginScreenRecorder Self;

	public:
		static const char kName[];
		static const char kEvent[];

	protected:
		PluginScreenRecorder();

	public:
		bool Initialize( lua_State *L, CoronaLuaRef listener );

	public:
		CoronaLuaRef GetListener() const { return fListener; }
        PreviewViewController *GetPreviewViewController() { return fPreviewViewController; }

	public:
		static int Open( lua_State *L );

	protected:
		static int Finalizer( lua_State *L );

	public:
		static Self *ToLibrary( lua_State *L );

	public:
		static int init( lua_State *L );
        static int start( lua_State *L );
        static int stop( lua_State *L );

	private:
		CoronaLuaRef fListener;
        PreviewViewController *fPreviewViewController;
};

// ----------------------------------------------------------------------------

// This corresponds to the name of the library, e.g. [Lua] require "plugin.screenRecorder"
const char PluginScreenRecorder::kName[] = "plugin.screenRecorder";

// This corresponds to the event name, e.g. [Lua] event.name
const char PluginScreenRecorder::kEvent[] = "screenRecorderComplete";

PluginScreenRecorder::PluginScreenRecorder()
:	fListener( NULL ), fPreviewViewController( NULL )
{
}

bool
PluginScreenRecorder::Initialize( lua_State *L, CoronaLuaRef listener )
{
    if ( fListener )
    {
        CoronaLuaDeleteRef( L, fListener );
    }

    fListener = listener;

	return true;
}

int
PluginScreenRecorder::Open( lua_State *L )
{
    // Register __gc callback
    const char kMetatableName[] = __FILE__; // Globally unique string to prevent collision
    CoronaLuaInitializeGCMetatable( L, kMetatableName, Finalizer );
    
    // Functions in library
    const luaL_Reg kVTable[] =
    {
        { "init", init },
        { "start", start },
        { "stop", stop },
        
        { NULL, NULL }
    };
    
    // Set library as upvalue for each library function
    Self *library = new Self;
    library->fPreviewViewController = [[PreviewViewController alloc] init];
	CoronaLuaPushUserdata( L, library, kMetatableName );

	luaL_openlib( L, kName, kVTable, 1 ); // leave "library" on top of stack

	return 1;
}

int
PluginScreenRecorder::Finalizer( lua_State *L )
{
	Self *library = (Self *)CoronaLuaToUserdata( L, 1 );

	CoronaLuaDeleteRef( L, library->GetListener() );

	delete library;

	return 0;
}

PluginScreenRecorder *
PluginScreenRecorder::ToLibrary( lua_State *L )
{
	// library is pushed as part of the closure
	Self *library = (Self *)CoronaLuaToUserdata( L, lua_upvalueindex( 1 ) );
	return library;
}

// [Lua] library.init( listener )
int
PluginScreenRecorder::init( lua_State *L )
{
	int listenerIndex = 1;

	if ( CoronaLuaIsListener( L, listenerIndex, kEvent ) )
	{
		Self *library = ToLibrary( L );

		CoronaLuaRef listener = CoronaLuaNewRef( L, listenerIndex );
		library->Initialize( L, listener );
	}

	return 0;
}


// [Lua] library.start()
int
PluginScreenRecorder::start(lua_State *L)
{
    Self *library = ToLibrary( L );
    int listenerIndex = 1;
    
    if ( CoronaLuaIsListener( L, listenerIndex, kEvent ) )
    {
        CoronaLuaRef listener = CoronaLuaNewRef( L, listenerIndex );
        library->Initialize( L, listener );
    }
    
    RPScreenRecorder *r = [RPScreenRecorder sharedRecorder];
    if ( !r.available) {
        NSLog(@"ReplayKit unavailable");
        return 0;
    }
    
    // 获取 Runtime 引用，用于在异步回调中获取安全的 Lua State
    id<CoronaRuntime> runtime = (id<CoronaRuntime>)CoronaLuaGetContext( L );
    
    if ( !r.recording ) {
        [r startRecordingWithHandler:^(NSError * _Nullable error) {
            // 切换回主线程处理 Lua 事件
            dispatch_async(dispatch_get_main_queue(), ^{
                bool isError = false;
                if ( error ) {
                    NSLog(@"Record start error info: %@", error.localizedDescription);
                    isError = true;
                }

                lua_State *L_safe = [runtime L];
                CoronaLuaRef listener = library->GetListener();
                
                if ( L_safe && listener ) {
                    // Create event and add message to it
                    CoronaLuaNewEvent( L_safe, kEvent );
                    lua_pushboolean( L_safe, isError );
                    lua_setfield( L_safe, -2, "isError" );
                    if (error) {
                        lua_pushstring( L_safe, [error.localizedDescription UTF8String] );
                    } else {
                        lua_pushstring( L_safe, "" );
                    }
                    lua_setfield( L_safe, -2, "errorMessage" );

                    // Dispatch event to library's listener
                    CoronaLuaDispatchEvent( L_safe, listener, 0 );
                }
            });
        }];
    }
    return 0;
}

// [Lua] library.stop()
int
PluginScreenRecorder::stop(lua_State *L)
{
    Self *library = ToLibrary( L );
    
    RPScreenRecorder *r = [RPScreenRecorder sharedRecorder];
    if ( !r.available) {
        NSLog(@"ReplayKit unavailable");
        return 0;
    }
    
    // 获取 Runtime 引用
    id<CoronaRuntime> runtime = (id<CoronaRuntime>)CoronaLuaGetContext( L );
    
    if ( r.recording ) {
        [r stopRecordingWithHandler:^(RPPreviewViewController * _Nullable previewViewController, NSError * _Nullable error) {
            // 切换回主线程处理 UI 和 Lua
            dispatch_async(dispatch_get_main_queue(), ^{
                if ( error ) {
                    NSLog(@"Record stop error info: %@", error.localizedDescription);
                } else {
                    if ( UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad ){
                        previewViewController.modalPresentationStyle = UIModalPresentationPopover;
                        // 修正 Popover 位置为屏幕中心，避免指向左上角 (0,0)
                        UIView *view = [runtime.appViewController view];
                        previewViewController.popoverPresentationController.sourceView = view;
                        previewViewController.popoverPresentationController.sourceRect = CGRectMake(view.bounds.size.width / 2, view.bounds.size.height / 2, 1, 1);
                        previewViewController.popoverPresentationController.permittedArrowDirections = 0; // 不显示箭头
                    }
                    previewViewController.previewControllerDelegate = (id<RPPreviewViewControllerDelegate>)library->GetPreviewViewController();
                    [runtime.appViewController presentViewController:previewViewController animated:YES completion:nil];
                }
            });
        }];
    }
    
    return 0;
}

// ----------------------------------------------------------------------------

CORONA_EXPORT int luaopen_plugin_screenRecorder( lua_State *L )
{
	return PluginScreenRecorder::Open( L );
}
