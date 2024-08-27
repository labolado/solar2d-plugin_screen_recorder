#import "PreviewViewController.h"

@implementation PreviewViewController

- (void)previewControllerDidFinish:(RPPreviewViewController *)previewController {
    [previewController dismissViewControllerAnimated:YES completion:^{
        NSLog(@"Dismiss complete!");
    }];
}

@synthesize hash;

@synthesize superclass;

@synthesize description;

@end
