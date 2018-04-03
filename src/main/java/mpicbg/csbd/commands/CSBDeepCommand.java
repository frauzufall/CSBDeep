/*-
 * #%L
 * CSBDeep: CNNs for image restoration of fluorescence microscopy.
 * %%
 * Copyright (C) 2017 - 2018 Deborah Schmidt, Florian Jug, Benjamin Wilhelm
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package mpicbg.csbd.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;

import javax.swing.JOptionPane;

import net.imagej.Dataset;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.display.DatasetView;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;

import org.scijava.Cancelable;
import org.scijava.Disposable;
import org.scijava.Initializable;
import org.scijava.ItemIO;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import mpicbg.csbd.imglib2.TiledView;
import mpicbg.csbd.network.ImageTensor;
import mpicbg.csbd.network.Network;
import mpicbg.csbd.network.task.DefaultInputMapper;
import mpicbg.csbd.network.task.DefaultModelExecutor;
import mpicbg.csbd.network.task.DefaultModelLoader;
import mpicbg.csbd.network.task.InputMapper;
import mpicbg.csbd.network.task.ModelExecutor;
import mpicbg.csbd.network.task.ModelLoader;
import mpicbg.csbd.network.tensorflow.TensorFlowNetwork;
import mpicbg.csbd.normalize.PercentileNormalizer;
import mpicbg.csbd.normalize.task.DefaultInputNormalizer;
import mpicbg.csbd.normalize.task.InputNormalizer;
import mpicbg.csbd.task.Task;
import mpicbg.csbd.task.TaskForceManager;
import mpicbg.csbd.task.TaskManager;
import mpicbg.csbd.tiling.AdvancedTiledView;
import mpicbg.csbd.tiling.DefaultTiling;
import mpicbg.csbd.tiling.Tiling;
import mpicbg.csbd.tiling.task.DefaultInputTiler;
import mpicbg.csbd.tiling.task.DefaultOutputTiler;
import mpicbg.csbd.tiling.task.InputTiler;
import mpicbg.csbd.tiling.task.OutputTiler;
import mpicbg.csbd.util.DatasetHelper;
import mpicbg.csbd.util.task.DefaultInputProcessor;
import mpicbg.csbd.util.task.DefaultOutputProcessor;
import mpicbg.csbd.util.task.InputProcessor;
import mpicbg.csbd.util.task.OutputProcessor;

public abstract class CSBDeepCommand implements Cancelable, Initializable, Disposable {

	@Parameter( type = ItemIO.INPUT, initializer = "processDataset" )
	public DatasetView datasetView;

	@Parameter
	protected LogService log;

	@Parameter( label = "Number of tiles", min = "1" )
	public int nTiles = 8;

	@Parameter( label = "Overlap between tiles", min = "0", stepSize = "16" )
	public int overlap = 32;

	@Parameter( type = ItemIO.OUTPUT )
	protected List< DatasetView > resultDatasets = new ArrayList<>();

	protected String modelFileUrl;
	protected String modelName;
	protected String inputNodeName = "input";
	protected String outputNodeName = "output";
	protected int blockMultiple = 32;

	protected TaskManager taskManager;

	protected Network network;
	protected Tiling tiling;

	protected InputProcessor inputProcessor;
	protected InputMapper inputMapper;
	protected InputNormalizer inputNormalizer;
	protected InputTiler inputTiler;
	protected ModelLoader modelLoader;
	protected ModelExecutor modelExecutor;
	protected OutputTiler outputTiler;
	protected OutputProcessor outputProcessor;

	@Override
	public void initialize() {
		initNetwork();
		initTasks();
	}

	protected void initNetwork() {
		network = new TensorFlowNetwork();
		network.loadLibrary();
	}

	private void initTasks() {
		inputMapper = initInputMapper();
		inputProcessor = initInputProcessor();
		inputNormalizer = initInputNormalizer();
		inputTiler = initInputTiler();
		modelLoader = initModelLoader();
		modelExecutor = initModelExecutor();
		outputTiler = initOutputTiler();
		outputProcessor = initOutputProcessor();
	}

	protected void initTaskManager() {
		final TaskForceManager tfm = new TaskForceManager();
		tfm.initialize();
		tfm.createTaskForce("Preprocessing", modelLoader, inputMapper, inputProcessor, inputNormalizer );
		tfm.createTaskForce("Tiling", inputTiler );
		tfm.createTaskForce("Execution", modelExecutor );
		tfm.createTaskForce("Postprocessing", outputTiler, outputProcessor );
		taskManager = tfm;
	}

	protected InputMapper initInputMapper() {
		return new DefaultInputMapper();
	}

	protected InputProcessor initInputProcessor() {
		return new DefaultInputProcessor();
	}

	protected InputNormalizer initInputNormalizer() {
		return new DefaultInputNormalizer();
	}

	protected InputTiler initInputTiler() {
		return new DefaultInputTiler();
	}

	protected ModelLoader initModelLoader() {
		return new DefaultModelLoader();
	}

	protected ModelExecutor initModelExecutor() {
		return new DefaultModelExecutor();
	}

	protected OutputTiler initOutputTiler() {
		return new DefaultOutputTiler();
	}

	protected OutputProcessor initOutputProcessor() {
		return new DefaultOutputProcessor();
	}

	public void run() {

		if ( noInputData() )
			return;

		prepareInputAndNetwork();
		final List< RandomAccessibleInterval< FloatType > > processedInput =
				inputProcessor.run( getInput() );

		final List< RandomAccessibleInterval< FloatType > > normalizedInput;
		if(doInputNormalization()) {
			setupNormalizer();
			normalizedInput = inputNormalizer.run( processedInput );
		} else {
			normalizedInput = processedInput;
		}

		initTiling();
		final List< AdvancedTiledView< FloatType > > tiledOutput =
				tryToTileAndRunNetwork( normalizedInput );
		final List< RandomAccessibleInterval< FloatType > > output =
				outputTiler.run( tiledOutput, tiling, getAxesArray( network.getOutputNode() ) );
		resultDatasets.clear();
		resultDatasets.addAll( outputProcessor.run( output, datasetView, network ) );

		dispose();

	}

	protected void setupNormalizer() {
		//do nothing, use normalizer default values
	}

	protected boolean doInputNormalization() {
		return true;
	}

	protected void prepareInputAndNetwork() {
		modelLoader.run(
				modelName,
				network,
				modelFileUrl,
				inputNodeName,
				outputNodeName,
				datasetView );
//		inputMapper.run( getInput(), network );
	}

	@Override
	public void dispose() {
		if(taskManager != null) {
			taskManager.close();
		}
		if(network != null) {
			network.dispose();
		}
		datasetView.dispose();
	}

	private AxisType[] getAxesArray( final ImageTensor outputNode ) {
		final AxisType[] res = new AxisType[ outputNode.numDimensions() + 1 ];
		for ( int i = 0; i < outputNode.numDimensions(); i++ ) {
			res[ i ] = outputNode.getAxisByDatasetDim( i );
		}
		res[ res.length - 1 ] = Axes.CHANNEL;
		return res;
	}

	protected void initTiling() {
		tiling = new DefaultTiling( nTiles, blockMultiple, overlap );
	}

	private List< AdvancedTiledView< FloatType > > tryToTileAndRunNetwork(
			final List< RandomAccessibleInterval< FloatType > > normalizedInput ) {
		List< AdvancedTiledView< FloatType > > tiledOutput = null;
		boolean isOutOfMemory = true;
		boolean canHandleOutOfMemory = true;
		while ( isOutOfMemory && canHandleOutOfMemory ) {
			try {
				final List< AdvancedTiledView< FloatType > > tiledInput =
						inputTiler.run( normalizedInput, getInput(), tiling );
				tiledOutput = modelExecutor.run( tiledInput, network );
				isOutOfMemory = false;
			} catch ( final OutOfMemoryError e ) {
				isOutOfMemory = true;
				canHandleOutOfMemory = tryHandleOutOfMemoryError();
			}
		}
		return tiledOutput;
	}

	public void setMapping( final AxisType[] mapping ) {
		inputMapper.setMapping( mapping );
	}

	private boolean noInputData() {
		return getInput() == null;
	}

	private boolean tryHandleOutOfMemoryError() {
		// We expect it to be an out of memory exception and
		// try it again with more tiles.
		final Task modelExecutorTask = ( Task ) modelExecutor;
		if ( !handleOutOfMemoryError() ) {
			modelExecutorTask.setFailed();
			return false;
		}
		modelExecutorTask.logError(
				"Out of memory exception occurred. Trying with " + nTiles + " tiles..." );
		modelExecutorTask.startNewIteration();
		( ( Task ) inputTiler ).addIteration();
		return true;
	}

	protected boolean handleOutOfMemoryError() {
		nTiles *= 2;
		// Check if the number of tiles is too large already
		if ( Arrays.stream(
				Intervals.dimensionsAsLongArray(
						getInput() ) ).max().getAsLong() / nTiles < blockMultiple ) { return false; }
		return true;
	}

	protected static void showError( final String errorMsg ) {
		JOptionPane.showMessageDialog(
				null,
				errorMsg,
				"Error",
				JOptionPane.ERROR_MESSAGE );
	}

	public Dataset getInput() {
		return datasetView.getData();
	}

	public void validateInput(
			final Dataset dataset,
			final String formatDesc,
			final OptionalLong... expectedDims ) throws IOException {
		DatasetHelper.validate( dataset, formatDesc, expectedDims );
	}

	@Override
	public String getCancelReason() {
		return null;
	}

	@Override
	public boolean isCanceled() {
		return false;
	}

	@Override
	public void cancel( final String reason ) {
		modelExecutor.cancel( reason );
		dispose();
	}

	protected void log(final String msg) {
		if(taskManager != null) {
			taskManager.log(msg);
		}else {
			System.out.println(msg);
		}
	}

	protected void
			printDim( final String title, final RandomAccessibleInterval< FloatType > img ) {
		//TODO fix print
		final long[] dims = new long[ img.numDimensions() ];
		img.dimensions( dims );
		log( title + ": " + Arrays.toString( dims ) );
	}

}
