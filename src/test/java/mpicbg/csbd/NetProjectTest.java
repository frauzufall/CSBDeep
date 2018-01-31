package mpicbg.csbd;

import static org.junit.Assert.assertTrue;

import java.util.List;

import net.imagej.Dataset;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.display.DatasetView;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.real.FloatType;

import org.junit.Ignore;
import org.junit.Test;

import mpicbg.csbd.commands.NetProject;


public class NetProjectTest extends CSBDeepTest {
	
//	@Ignore
	@Test
	public void testNetProject_Float_XYZ() {
		testDataset(new FloatType(), new long[] {100, 50, 10}, new AxisType[] {Axes.X, Axes.Y, Axes.Z});
	}
	
	@Ignore
	@Test
	public void testNetProject_Float_ZXY() {
		testDataset(new FloatType(), new long[] {10, 50, 100}, new AxisType[] {Axes.Z, Axes.X, Axes.Y});
	}
	
//	@Ignore
	@Test
	public void testNetProject_Float_XZY() {
		testDataset(new FloatType(), new long[] {100, 10, 50}, new AxisType[] {Axes.X, Axes.Z, Axes.Y});
	}
	
	@Ignore
	@Test
	public void testNetProject_Byte_XZY() {
		testDataset(new ByteType(), new long[] {50, 10, 100}, new AxisType[] {Axes.X, Axes.Z, Axes.Y});
	}
	
	public <T extends RealType<T> & NativeType<T>> void testDataset(T type, long[] dims, AxisType[] axes) {

		launchImageJ();
		final Dataset input = createDataset(type, dims, axes);
		final DatasetView datasetView = wrapInDatasetView( input );
		final List<DatasetView> result = runPlugin(NetProject.class, datasetView);
		assertTrue("result should contain one dataset", result.size() == 1);
		final Dataset output = result.get( 0 ).getData();
		testResultAxesAndSizeByRemovingZ(input, output);
	}

}
