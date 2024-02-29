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
		bool Initialize( CoronaLuaRef listener );

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
		static int show( lua_State *L );
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
PluginScreenRecorder::Initialize( CoronaLuaRef listener )
{
	// Can only initialize listener once
	bool result = ( NULL == fListener );

	if ( result )
	{
		fListener = listener;
	}

	return result;
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
        { "show", show },
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
		library->Initialize( listener );
	}

	return 0;
}

// [Lua] library.show( word )
int
PluginScreenRecorder::show( lua_State *L )
{
	NSString *message = @"Error: Could not display UIReferenceLibraryViewController. This feature requires iOS 5 or later.";
	
	if ( [UIReferenceLibraryViewController class] )
	{
		id<CoronaRuntime> runtime = (id<CoronaRuntime>)CoronaLuaGetContext( L );

		const char kDefaultWord[] = "corona";
		const char *word = lua_tostring( L, 1 );
		if ( ! word )
		{
			word = kDefaultWord;
		}

		UIReferenceLibraryViewController *controller = [[[UIReferenceLibraryViewController alloc] initWithTerm:[NSString stringWithUTF8String:word]] autorelease];

		// Present the controller modally.
		[runtime.appViewController presentViewController:controller animated:YES completion:nil];

		message = @"Success. Displaying UIReferenceLibraryViewController for 'corona'.";
	}

	Self *library = ToLibrary( L );

	// Create event and add message to it
	CoronaLuaNewEvent( L, kEvent );
	lua_pushstring( L, [message UTF8String] );
	lua_setfield( L, -2, "message" );

	// Dispatch event to library's listener
	CoronaLuaDispatchEvent( L, library->GetListener(), 0 );

	return 0;
}

// [Lua] library.start()
int
PluginScreenRecorder::start(lua_State *L)
{
    RPScreenRecorder *r = [RPScreenRecorder sharedRecorder];
    if ( !r.available) {
        NSLog(@"ReplayKit unavailable");
        return 0;
    }
    if ( !r.recording ) {
        [r startRecordingWithHandler:^(NSError * _Nullable error) {
            NSLog(@"Record start error info: %@", error.localizedDescription);
        }];
    }
    return 0;
}

// [Lua] library.stop()
int
PluginScreenRecorder::stop(lua_State *L)
{
    Self *library = ToLibrary( L );
    // NSLog(@"previewViewController is null: %@", library->GetPreviewViewController() == NULL ? @"YES" : @"NO");
    
    RPScreenRecorder *r = [RPScreenRecorder sharedRecorder];
    if ( !r.available) {
        NSLog(@"ReplayKit unavailable");
        return 0;
    }
    if ( r.recording ) {
        [r stopRecordingWithHandler:^(RPPreviewViewController * _Nullable previewViewController, NSError * _Nullable error) {
            if ( error ) {
                NSLog(@"Record stop error info: %@", error.localizedDescription);
            } else {
                id<CoronaRuntime> runtime = (id<CoronaRuntime>)CoronaLuaGetContext( L );
                if ( UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad ){
                    previewViewController.modalPresentationStyle = UIModalPresentationPopover;
                    previewViewController.popoverPresentationController.sourceRect = CGRect();
                    previewViewController.popoverPresentationController.sourceView = [runtime.appViewController view];
                }
                previewViewController.previewControllerDelegate = (id<RPPreviewViewControllerDelegate>)library->GetPreviewViewController();
                [runtime.appViewController presentViewController:previewViewController animated:YES completion:nil];
            }
        }];
    }
    
    return 0;
}

// ----------------------------------------------------------------------------

CORONA_EXPORT int luaopen_plugin_screenRecorder( lua_State *L )
{
	return PluginScreenRecorder::Open( L );
}
