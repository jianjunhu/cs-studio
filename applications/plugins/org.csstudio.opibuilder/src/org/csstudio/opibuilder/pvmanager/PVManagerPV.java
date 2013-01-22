package org.csstudio.opibuilder.pvmanager;

import static org.epics.pvmanager.ExpressionLanguage.channel;
import static org.epics.pvmanager.ExpressionLanguage.newValuesOf;
import static org.epics.pvmanager.formula.ExpressionLanguage.formula;
import static org.epics.util.time.TimeDuration.ofMillis;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.csstudio.data.values.IValue;
import org.csstudio.opibuilder.util.ErrorHandlerUtil;
import org.csstudio.utility.pv.PV;
import org.csstudio.utility.pv.PVListener;
import org.epics.pvmanager.ExceptionHandler;
import org.epics.pvmanager.PVManager;
import org.epics.pvmanager.PVReader;
import org.epics.pvmanager.PVReaderEvent;
import org.epics.pvmanager.PVReaderListener;
import org.epics.pvmanager.PVWriter;
import org.epics.pvmanager.PVWriterListener;

/**A utility PV which uses PVManager as the connection layer. 
 * Type of the value returned by {@link #getValue()} is always {@link PMObjectValue}.
 * @author Xihui Chen
 *
 */
public class PVManagerPV implements PV {
	
	private final static ExecutorService PMPV_THREAD = Executors.newSingleThreadExecutor();
	final private String name;
	final private boolean valueBuffered;
	private Map<PVListener, PVReaderListener<Object>> listenerMap;
	private List<PVWriterListener<Object>> pvWriterListeners;
	private ExceptionHandler exceptionHandler = new ExceptionHandler() {
		@Override
		public void handleException(Exception ex) {
			ErrorHandlerUtil.handleError("Error from PVManager: ", ex);
		}
	};
	private PVReader<?> pvReader;
	private PVWriter<Object> pvWriter;
	private int updateDuration;
	/**Construct a PVManger PV.
	 * @param name name of the pv.
	 * @param bufferAllValues true if all values should be buffered.
	 * @param updateDuration the least update duration.
	 */
	public PVManagerPV(String name, boolean bufferAllValues, int updateDuration) {
		this.name = name;
		this.valueBuffered = bufferAllValues;
		this.updateDuration = updateDuration;
		listenerMap = new LinkedHashMap<PVListener, PVReaderListener<Object>>();	
		pvWriterListeners = new LinkedList<>();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public IValue getValue(double timeout_seconds) throws Exception {
		return getValue();
	}

	@Override
	public synchronized void addListener(final PVListener listener) {		
		final PVReaderListener<Object> pvReaderListener = new PVReaderListener<Object>() {
	
			@Override
			public void pvChanged(PVReaderEvent<Object> event) {
				if (event != null && event.isConnectionChanged() 
						&& !pvReader.isConnected()) {
					listener.pvDisconnected(PVManagerPV.this);
					return;
				}
				if(event == null || event.isValueChanged())
					listener.pvValueUpdate(PVManagerPV.this);
			}
		};
		listenerMap.put(listener, pvReaderListener);
		if(pvReader !=null){
			//give an update on current value in PMPV thread.
			if(!pvReader.isClosed() && pvReader.isConnected() && !pvReader.isPaused()){
				PMPV_THREAD.execute(new Runnable() {						
						@Override
						public void run() {							
							pvReaderListener.pvChanged(null);
						}
					});
			}
			
			pvReader.addPVReaderListener(pvReaderListener);			
		}
	}
	
	public synchronized void addPVWriterListener(final PVWriterListener<Object> listener){
		pvWriterListeners.add(listener);
		if(pvWriter != null){
			if(!pvWriter.isClosed()){
				PMPV_THREAD.execute(new Runnable() {
					
					@Override
					public void run() {
						listener.pvChanged(null);
					}
				});
			}
		}
	}

	@Override
	public synchronized void removeListener(PVListener listener) {
		if (!listenerMap.containsKey(listener))
			return;
		if(pvReader != null)
			pvReader.removePVReaderListener(listenerMap.get(listener));
		listenerMap.remove(listener);
	}


	@Override
	public synchronized void start() throws Exception {		
		if (pvReader == null) {
			PMPV_THREAD.execute(new Runnable() {
				
				@Override
				public void run() {
					internalStart();
				}
			});
		} else
			pvReader.setPaused(false);
	}

	/**
	 * This method must be called in PMPV thread, because PVManager requires
	 * that creating PVReader, adding listeners must be done in the notification
	 * thread and must be in the same runnable to make sure no updates are missed. 
	 */
	private void internalStart() {
		
		if (valueBuffered) {
			pvReader = PVManager.read(newValuesOf(channel(name)))
					.notifyOn(PMPV_THREAD)
					.routeExceptionsTo(exceptionHandler)
					.maxRate(ofMillis(updateDuration));
		} else {
			pvReader = PVManager.read(formula(name)).notifyOn(PMPV_THREAD)
					.routeExceptionsTo(exceptionHandler)
					.maxRate(ofMillis(updateDuration));
		}
		for(PVReaderListener<Object> pvReaderListener : listenerMap.values())
			pvReader.addPVReaderListener(pvReaderListener);		
		
		pvWriter = PVManager.write(channel(name)).notifyOn(PMPV_THREAD)
				.routeExceptionsTo(exceptionHandler).async();
		
		for(PVWriterListener<Object> pvWriterListener : pvWriterListeners){
			pvWriter.addPVWriterListener(pvWriterListener);
		}

	}

	public boolean isValueBuffered() {
		return valueBuffered;
	}
	
	@Override
	public synchronized boolean isRunning() {
		if(pvReader == null) 
			return false;
		return !pvReader.isClosed() && !pvReader.isPaused();
	}

	@Override
	public synchronized boolean isConnected() {
		if(pvReader == null) 
			return false;
		return pvReader.isConnected() && pvReader.getValue() != null;
	}

	@Override
	public synchronized boolean isWriteAllowed() {
		if(pvWriter == null) 
			return false;
		return pvWriter.isWriteConnected();
	}

	
	@Override
	public synchronized String getStateInfo() {
		checkIfPVStarted();
		StringBuilder stateInfo = new StringBuilder();
		if (pvReader.isConnected()) {
			stateInfo.append("Connected");
			if (isRunning())
				stateInfo.append(" Running");
			if (pvReader.isPaused())
				stateInfo.append(" Paused");
			if (pvReader.isClosed())
				stateInfo.append(" Closed");
		} else
			stateInfo.append("Connecting");
		return stateInfo.toString();
	}

	/**
	 * Note that PVManagerPV.stop will remove all listeners and close
	 * connections. The PV cannot be accessed after stop. If you need the PV
	 * accessible later using {@link #start()}, you should call
	 * {@link #getPmPV()}.setPaused(true).
	 * 
	 * @see org.csstudio.utility.pv.PV#stop()
	 */
	@Override
	public synchronized void stop() {
		if(pvReader != null)
			pvReader.close();
		if(pvWriter != null)
			pvWriter.close();
		pvReader = null;
		pvWriter = null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public synchronized IValue getValue() {
		checkIfPVStarted();
		if(pvReader.getValue() != null){
			if(!valueBuffered || 
					(valueBuffered && ((List<Object>)(pvReader.getValue())).size()>0))
			return new PMObjectValue(pvReader.getValue(), valueBuffered);
		}
		return null;
	}

	@Override
	public void setValue(Object new_value) throws Exception {
		checkIfPVStarted();
		pvWriter.write(new_value);
	}
	
	private void checkIfPVStarted(){
		if(pvReader == null)
			throw new IllegalStateException("PVManagerPV is not started yet.");
	}
		
}
