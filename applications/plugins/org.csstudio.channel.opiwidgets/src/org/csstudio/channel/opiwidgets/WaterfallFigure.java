package org.csstudio.channel.opiwidgets;

import java.lang.reflect.Constructor;

import org.csstudio.opibuilder.model.AbstractContainerModel;
import org.csstudio.opibuilder.widgets.figures.AbstractSWTWidgetFigure;
import org.csstudio.utility.pvmanager.widgets.WaterfallWidget;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

public class WaterfallFigure extends AbstractSWTWidgetFigure {
	
	public WaterfallFigure(Composite composite, AbstractContainerModel parentModel) {
		super(composite, parentModel);
		widget = createWaterfallWidget(composite);
	}
	
	private WaterfallWidget createWaterfallWidget(Composite parent) {
		try {
			Class<?> clazz = Class.forName("org.csstudio.channel.widgets.MultiChannelWaterfallWidget");
			Constructor<?> constructor = clazz.getConstructor(Composite.class, Integer.TYPE);
			return (WaterfallWidget) constructor.newInstance(parent, SWT.NONE);
		} catch (Exception e) {
		}
		return new WaterfallWidget(parent, SWT.NONE);
	}
	
	private WaterfallWidget widget;

	@Override
	public WaterfallWidget getSWTWidget() {
		return widget;
	}
	
	public boolean isRunMode() {
		return runmode;
	}
}