package tornado.drivers.opencl.mm;

import java.util.List;

import com.oracle.graal.api.meta.Kind;

import tornado.api.Event;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.OCLEvent;

public class OCLDoubleArrayWrapper extends OCLArrayWrapper<double[]> {

	public OCLDoubleArrayWrapper(OCLDeviceContext deviceContext) {
		this(deviceContext, false);
	}
	
	public OCLDoubleArrayWrapper(OCLDeviceContext deviceContext, boolean isFinal) {
		super(deviceContext, Kind.Double, isFinal);
	}

	@Override
	protected void readArrayData(long bufferId, long offset, long bytes,
			double[] value, List<Event> waitEvents) {
		deviceContext.readBuffer(bufferId, offset, bytes, value, waitEvents);		
	}

	@Override
	protected void writeArrayData(long bufferId, long offset, long bytes,
			double[] value, List<Event> waitEvents) {
		deviceContext.writeBuffer(bufferId, offset, bytes, value, waitEvents);
	}

	@Override
	protected OCLEvent enqueueReadArrayData(long bufferId, long offset,
			long bytes, double[] value, List<Event> waitEvents) {
		return deviceContext.enqueueReadBuffer(bufferId, offset, bytes, value, waitEvents);
	}

	@Override
	protected OCLEvent enqueueWriteArrayData(long bufferId, long offset,
			long bytes, double[] value, List<Event> waitEvents) {
		return deviceContext.enqueueWriteBuffer(bufferId, offset, bytes, value, waitEvents);
	}
	
}
