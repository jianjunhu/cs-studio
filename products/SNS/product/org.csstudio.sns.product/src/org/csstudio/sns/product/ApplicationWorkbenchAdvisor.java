package org.csstudio.sns.product;

import org.csstudio.startup.application.OpenDocumentEventProcessor;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.application.IWorkbenchConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;

/** Tell the workbench how to behave.
 *  @author Kay Kasemir
 */
public class ApplicationWorkbenchAdvisor extends WorkbenchAdvisor
{
	private OpenDocumentEventProcessor openDocProcessor;
	
    public ApplicationWorkbenchAdvisor(
			OpenDocumentEventProcessor openDocProcessor) {
    	this.openDocProcessor = openDocProcessor;
    }

	@Override
    public WorkbenchWindowAdvisor createWorkbenchWindowAdvisor(
                    IWorkbenchWindowConfigurer configurer)
    {
        return new ApplicationWorkbenchWindowAdvisor(configurer);
    }

    @Override
    public void initialize(IWorkbenchConfigurer configurer)
    {
        // Per default, state is not preserved (RCP book 5.1.1)
        configurer.setSaveAndRestore(true);
    }

    /** @return ID of initial perspective */
    @Override
    public String getInitialWindowPerspectiveId()
    {
        return CSS_Perspective.ID;
    }
    
    @Override
    public void eventLoopIdle(Display display) {
    	if(openDocProcessor != null)
    		openDocProcessor.catchUp(display);
    	super.eventLoopIdle(display);
    }
}
