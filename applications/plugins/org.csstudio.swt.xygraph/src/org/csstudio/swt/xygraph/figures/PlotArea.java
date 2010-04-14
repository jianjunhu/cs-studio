package org.csstudio.swt.xygraph.figures;

import java.util.ArrayList;
import java.util.List;

import org.csstudio.swt.xygraph.linearscale.Range;
import org.csstudio.swt.xygraph.undo.ZoomCommand;
import org.csstudio.swt.xygraph.undo.ZoomType;
import org.csstudio.swt.xygraph.util.XYGraphMediaFactory;
import org.csstudio.swt.xygraph.util.XYGraphMediaFactory.CURSOR_TYPE;
import org.eclipse.draw2d.Figure;
import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.MouseEvent;
import org.eclipse.draw2d.MouseListener;
import org.eclipse.draw2d.MouseMotionListener;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

/**The plot area figure.
 * @author Xihui Chen
 */
public class PlotArea extends Figure {
    /** The ratio of the shrink/expand area for one zoom. */
    final private static double ZOOM_RATIO = 0.1;
    
    /** The auto zoom interval in ms.*/
    final private static int ZOOM_SPEED = 200;
    
    final private XYGraph xyGraph;
	final private List<Trace> traceList = new ArrayList<Trace>();
	final private List<Grid> gridList = new ArrayList<Grid>();
	final private List<Annotation> annotationList = new ArrayList<Annotation>();
	
	final private List<Range> xAxisStartRangeList = new ArrayList<Range>();
	final private List<Range> yAxisStartRangeList = new ArrayList<Range>();
	    
	final private Cursor grabbing;

	private boolean showBorder;	
	
	private ZoomType zoomType;
	
	private Point start;
	private Point end;
	private boolean armed;
	
	private Color revertBackColor;
	
	public PlotArea(final XYGraph xyGraph) {
		this.xyGraph = xyGraph;
		setBackgroundColor(XYGraph.WHITE_COLOR);
		setForegroundColor(XYGraph.BLACK_COLOR);		
		setOpaque(true);
		RGB backRGB = getBackgroundColor().getRGB();
		revertBackColor = XYGraphMediaFactory.getInstance().getColor(255- backRGB.red, 
				255 - backRGB.green, 255 - backRGB.blue);
		PlotAreaZoomer zoomer = new PlotAreaZoomer();
		addMouseListener(zoomer);
		addMouseMotionListener(zoomer);
		grabbing = XYGraphMediaFactory.getCursor(CURSOR_TYPE.GRABBING);
		zoomType = ZoomType.NONE;
	}
	
	@Override
	public void setBackgroundColor(Color bg) {
		RGB backRGB = bg.getRGB();
		revertBackColor = XYGraphMediaFactory.getInstance().getColor(255- backRGB.red, 
				255 - backRGB.green, 255 - backRGB.blue);
		super.setBackgroundColor(bg);
		
	}
	
	/**Add a trace to the plot area.
	 * @param trace the trace to be added.
	 */
	public void addTrace(Trace trace){
		traceList.add(trace);
		add(trace);
		revalidate();
	}
	
	/**Remove a trace from the plot area.
	 * @param trace
	 * @return true if this plot area contained the specified trace
	 */	
	public boolean removeTrace(Trace trace){
		boolean result = traceList.remove(trace);
		if(result){
			remove(trace);
			revalidate();
		}
		return result;
	}
	
	/**Add a grid to the plot area.
	 * @param grid the grid to be added.
	 */
	public void addGrid(Grid grid){
		gridList.add(grid);
		add(grid);
		revalidate();
	}
	
	/**Remove a grid from the plot area.
	 * @param grid the grid to be removed.
	 * @return true if this plot area contained the specified grid
	 */	
	public boolean removeGrid(Grid grid){
		boolean result = gridList.remove(grid);
		if(result){
			remove(grid);
			revalidate();
		}
		return result;
	}
	
	
	
	/**Add an annotation to the plot area.
	 * @param annotation the annotation to be added.
	 */
	public void addAnnotation(Annotation annotation){
		annotationList.add(annotation);
		annotation.setxyGraph(xyGraph);
		add(annotation);
		revalidate();
	}
	
	/**Remove a annotation from the plot area.
	 * @param annotation the annotation to be removed.
	 * @return true if this plot area contained the specified annotation
	 */	
	public boolean removeAnnotation(Annotation annotation){
		boolean result = annotationList.remove(annotation);
		if(!annotation.isFree())
			annotation.getTrace().getDataProvider().removeDataProviderListener(annotation);
		if(result){
			remove(annotation);
			revalidate();
		}
		return result;
	}
	
	
	
	@Override
	protected void layout() {
		Rectangle clientArea = getClientArea();
		for(Trace trace : traceList){
			if(trace != null && trace.isVisible())
				//Shrink will make the trace has no intersection with axes,
				//which will make it only repaints the trace area.
				trace.setBounds(clientArea);//.getCopy().shrink(1, 1));				
		}		
		for(Grid grid : gridList){
			if(grid != null && grid.isVisible())
				grid.setBounds(clientArea);
		}
		
		for(Annotation annotation : annotationList){
			if(annotation != null && annotation.isVisible())
				annotation.setBounds(clientArea);//.getCopy().shrink(1, 1));
		}
		super.layout();
	}
	
	@Override
	protected void paintClientArea(Graphics graphics) {
		super.paintClientArea(graphics);
		if(showBorder){
			graphics.setLineWidth(2);
			graphics.drawLine(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y);
			graphics.drawLine(bounds.x + bounds.width, bounds.y, 
				bounds.x + bounds.width, bounds.y + bounds.height );
		}
		if(armed && end != null && start != null){
			switch (zoomType) {
			case RUBBERBAND_ZOOM:
			case HORIZONTAL_ZOOM:
			case VERTICAL_ZOOM:
				graphics.setLineStyle(SWT.LINE_DOT);
				graphics.setLineWidth(1);				
				graphics.setForegroundColor(revertBackColor);
				graphics.drawRectangle(start.x, start.y, end.x - start.x, end.y - start.y);
				break;
	
			default:
				break;
			}
			
		}
	}
	
	/**
	 * @param showBorder the showBorder to set
	 */
	public void setShowBorder(boolean showBorder) {
		this.showBorder = showBorder;
	}

	/**
	 * @return the showBorder
	 */
	public boolean isShowBorder() {
		return showBorder;
	}

	/**
	 * @param zoomType the zoomType to set
	 */
	public void setZoomType(ZoomType zoomType) {
		this.zoomType = zoomType;
		setCursor(zoomType.getCursor());
	}
	
	/** Perform rubberband or horiz/vertical zoom based on
	 *  mouse pointer start/end coordinates
	 */
	private void zoom(){
		double t1, t2;
		for(Axis axis : xyGraph.getXAxisList()){
			t1 = axis.getPositionValue(start.x, false);
			t2 = axis.getPositionValue(end.x, false);
			axis.setRange(t1, t2);			
		}
		for(Axis axis : xyGraph.getYAxisList()){
			t1 = axis.getPositionValue(start.y, false);
			t2 = axis.getPositionValue(end.y, false);
			axis.setRange(t1, t2);			
		}
	}
	
    /** Zoom 'in' or 'out' by a fixed factor
     *  @param horizontally along x axes?
     *  @param vertically along y axes?
     *  @param factor Zoom factor. Positive to zoom 'in', negative 'out'.
     */
	private void zoomInOut(final boolean horizontally, final boolean vertically,
	                    final double factor)
	{
        if (horizontally)
    		for(Axis axis : xyGraph.getXAxisList())
    		{
    			final double center = axis.getPositionValue(start.x, false);
    			axis.zoomInOut(center, factor);
    		}	
        if (vertically)
    		for(Axis axis : xyGraph.getYAxisList())
    		{
    		    final double center = axis.getPositionValue(start.y, false);
                axis.zoomInOut(center, factor);
    		}		
	}
	
	/**
	 * @return the traceList
	 */
	public List<Trace> getTraceList() {
		return traceList;
	}

	/**
	 * @return the annotationList
	 */
	public List<Annotation> getAnnotationList() {
		return annotationList;
	}

	private void pan(){
		double t1, t2, m;
		int i=0;
		Range temp;
		for(Axis axis : xyGraph.getXAxisList()){
			t1 = axis.getPositionValue(start.x, false);
			t2 = axis.getPositionValue(end.x, false);
			temp = xAxisStartRangeList.get(i);
			if(axis.isLogScaleEnabled()){
				m = Math.log10(t2) - Math.log10(t1);
				t1 = Math.pow(10,Math.log10(temp.getLower()) - m);
				t2 = Math.pow(10,Math.log10(temp.getUpper()) - m);
			}else {
				m = t2-t1;
				t1 = temp.getLower() - m;
				t2 = temp.getUpper() - m;
			}
			axis.setRange(t1, t2);
			i++;
		}
		i=0;
		for(Axis axis : xyGraph.getYAxisList()){			
			t1 = axis.getPositionValue(start.y, false);
			t2 = axis.getPositionValue(end.y, false);
			temp = yAxisStartRangeList.get(i);
			if(axis.isLogScaleEnabled()){				
				m = Math.log10(t2) - Math.log10(t1);
				t1 = Math.pow(10.0,Math.log10(temp.getLower()) - m);
				t2 = Math.pow(10.0,(Math.log10(temp.getUpper()) - m));
			}
			else{
				m = t2-t1;
				t1 = temp.getLower() - m;
				t2 = temp.getUpper() - m;
			}
			axis.setRange(t1, t2);
			i++;
		}
	}

	class PlotAreaZoomer extends MouseMotionListener.Stub implements MouseListener{	
		
		private ZoomCommand command;
		
		@Override
		public void mouseDragged(final MouseEvent me) {
			if(!armed)
				return;
			switch (zoomType) {
			case RUBBERBAND_ZOOM:
				end = me.getLocation();				
				break;
			case HORIZONTAL_ZOOM:
				end = new Point(me.getLocation().x, bounds.y + bounds.height);
				break;
			case VERTICAL_ZOOM:
				end = new Point(bounds.x + bounds.width, me.getLocation().y);
				break;
			case PANNING:
				end = me.getLocation();
				pan();
				break;
			default:
				break;
			}
			PlotArea.this.repaint();
		}
		
		public void mouseDoubleClicked(MouseEvent me) {}

		public void mousePressed(final MouseEvent me) {	
		    // Only react to 'main' mouse button, only react to 'real' zoom
		    if (me.button != 1 ||
                zoomType == ZoomType.NONE)
				return;
			armed = true;
			//get start position
			switch (zoomType) {
			case RUBBERBAND_ZOOM:
				start = me.getLocation();
				end = null;
				break;
			case HORIZONTAL_ZOOM:
				start = new Point(me.getLocation().x, bounds.y);
				end = null;
				break;
			case VERTICAL_ZOOM:
				start = new Point(bounds.x, me.getLocation().y);
				end = null;
				break;
			case PANNING:
				setCursor(grabbing);
				start = me.getLocation();
				end = null;
				xAxisStartRangeList.clear();
				yAxisStartRangeList.clear();
				for(Axis axis : xyGraph.getXAxisList())
					xAxisStartRangeList.add(axis.getRange());
				for(Axis axis : xyGraph.getYAxisList())
					yAxisStartRangeList.add(axis.getRange());
				break;
				
			case ZOOM_IN:
            case ZOOM_IN_HORIZONTALLY:
            case ZOOM_IN_VERTICALLY:
			case ZOOM_OUT:
			case ZOOM_OUT_HORIZONTALLY:
			case ZOOM_OUT_VERTICALLY:
				start = me.getLocation();
				end = new Point();
				// Start timer that will zoom while mouse button is pressed
				Display.getCurrent().timerExec(ZOOM_SPEED, new Runnable(){
					public void run() {	
						if(armed){
						    switch (zoomType)
						    {
						    case ZOOM_IN:              zoomInOut(true, true,  ZOOM_RATIO); break;
						    case ZOOM_IN_HORIZONTALLY: zoomInOut(true, false, ZOOM_RATIO); break;
						    case ZOOM_IN_VERTICALLY:   zoomInOut(false, true, ZOOM_RATIO); break;
						    case ZOOM_OUT: 			   zoomInOut(true, true, -ZOOM_RATIO); break;
						    case ZOOM_OUT_HORIZONTALLY:zoomInOut(true, false,-ZOOM_RATIO); break;
						    case ZOOM_OUT_VERTICALLY:  zoomInOut(false, true,-ZOOM_RATIO); break;
						    default:                   // NOP
						    }
							Display.getCurrent().timerExec(ZOOM_SPEED, this);
						}
					}
				});
				break;
			default:
				break;
			}
			
			//add command for undo operation
			command = new ZoomCommand(zoomType.getDescription(), 
					xyGraph.getXAxisList(), xyGraph.getYAxisList());
			command.savePreviousStates();
			me.consume();			
		}

		@Override
		public void mouseExited(MouseEvent me) {
			//make sure the zoomIn/Out timer could be stopped
		    switch (zoomType)
            {
            case ZOOM_IN:
            case ZOOM_IN_HORIZONTALLY:
            case ZOOM_IN_VERTICALLY:
            case ZOOM_OUT:
            case ZOOM_OUT_HORIZONTALLY:
            case ZOOM_OUT_VERTICALLY:
				mouseReleased(me);
		    default:
            }
		}
		
		public void mouseReleased(final MouseEvent me) {
			if(zoomType == ZoomType.PANNING)
				setCursor(zoomType.getCursor());
			if(!armed || end == null || start == null)
				return;
			
			switch (zoomType) {
			case RUBBERBAND_ZOOM:
			case HORIZONTAL_ZOOM:
			case VERTICAL_ZOOM:
				zoom();
				break;
			case PANNING:
				pan();					
				break;	
			case ZOOM_IN:             
			    zoomInOut(true, true,  2*ZOOM_RATIO);
			     break;
            case ZOOM_IN_HORIZONTALLY:
                zoomInOut(true, false, 2*ZOOM_RATIO);
                break;
            case ZOOM_IN_VERTICALLY:
                zoomInOut(false, true, 2*ZOOM_RATIO);
                break;
            case ZOOM_OUT:
                zoomInOut(true, true, -2*ZOOM_RATIO);
                break;
            case ZOOM_OUT_HORIZONTALLY:
                zoomInOut(true, false,-2*ZOOM_RATIO);
                break;
            case ZOOM_OUT_VERTICALLY:
                zoomInOut(false, true,-2*ZOOM_RATIO);
                break;
			default:
				break;
			}
			
			if(zoomType != ZoomType.NONE && command != null){
				command.saveAfterStates();
				xyGraph.getOperationsManager().addCommand(command);				
			}		
			armed = false;
			end = null; 
			start = null;			
			PlotArea.this.repaint();
		}
	}
}
