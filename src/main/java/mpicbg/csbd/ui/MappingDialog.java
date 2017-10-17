package mpicbg.csbd.ui;

import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.tensorflow.framework.SignatureDef;

import mpicbg.csbd.tensorflow.DatasetTensorBridge;

public class MappingDialog {

	public static void create( final DatasetTensorBridge bridge, final SignatureDef sig ) {

		if ( bridge.complete() ) {
			final List< JComboBox< String > > drops = new ArrayList<>();

			final JPanel dialogPanel = new JPanel();
			final JPanel inputDimPanel = new JPanel();
			final JPanel imgDimPanel = new JPanel();
			final JPanel mappingPanel = new JPanel();

			imgDimPanel.setBorder( BorderFactory.createTitledBorder( "Image" ) );
			mappingPanel.setBorder( BorderFactory.createTitledBorder( "Mapping" ) );

			inputDimPanel.setBorder( BorderFactory.createTitledBorder( "Model input" ) );

			final List< String > dimStringsLength = new ArrayList<>();
			for ( int i = 0; i < bridge.numDimensions(); i++ ) {
				final JTextField field = new JTextField();
				field.setText( String.valueOf( bridge.getDatasetDimLength( i ) ) );
				field.setEditable( false );
				imgDimPanel.add(
						new JLabel( bridge.getDatasetDimName( i ) + ":", SwingConstants.RIGHT ) );
				imgDimPanel.add( field );
				dimStringsLength.add(
						bridge.getDatasetDimName( i ) + " [" + bridge.getDatasetDimLength(
								i ) + "]" );
			}

			int dimCount = 0;
			for ( int i = 0; i < bridge.numDimensions(); i++ ) {
				if ( bridge.getMapping( i ) >= 0 ) {
					final JTextField field = new JTextField();
					field.setText(
							String.valueOf(
									bridge.getAbstractInputTensorShape().getDim( dimCount ).getSize() ) );
					field.setEditable( false );
					inputDimPanel.add( new JLabel( dimCount + ":", SwingConstants.RIGHT ) );
					inputDimPanel.add( field );
					inputDimPanel.add( field );
					final String[] dimStringsLengthArr = new String[ dimStringsLength.size() ];
					for ( int j = 0; j < dimStringsLength.size(); j++ ) {
						dimStringsLengthArr[ j ] = dimStringsLength.get( j );
					}
					final JComboBox< String > dimDrop = new JComboBox<>( dimStringsLengthArr );
					dimDrop.setSelectedIndex( bridge.getMapping( i ) );
					mappingPanel.add(
							new JLabel( dimCount + " [" + bridge.getAbstractInputTensorShape().getDim(
									dimCount ).getSize() + "] :", SwingConstants.RIGHT ) );
					mappingPanel.add( dimDrop );
					drops.add( dimDrop );

					dimCount++;
				}
			}

			final GridLayout col1Layout = new GridLayout( 0, 1 );
			final GridLayout col5Layout = new GridLayout( 0, 10 );
			col5Layout.setHgap( 15 );
			col1Layout.setVgap( 15 );

			imgDimPanel.setLayout( col5Layout );
			inputDimPanel.setLayout( col5Layout );
			mappingPanel.setLayout( col5Layout );
			dialogPanel.setLayout( col1Layout );

			dialogPanel.add( imgDimPanel );

			dialogPanel.add( inputDimPanel );

			dialogPanel.add( mappingPanel );

			final int result = JOptionPane.showConfirmDialog(
					null,
					dialogPanel,
					"Please match image and tensorflow model dimensions",
					JOptionPane.OK_CANCEL_OPTION );
			if ( result == JOptionPane.OK_OPTION ) {
				bridge.printMapping();
				for ( int i = 0; i < drops.size(); i++ ) {
					System.out.println(
							"selected index for tf index " + i + ": " + drops.get(
									i ).getSelectedIndex() );
					bridge.setMappingInputTensorDim( i, drops.get( i ).getSelectedIndex() );
				}
				bridge.printMapping();
			}
		} else {
			System.out.println(
					"Model and/or input image not initialized. call updateModel(); and updateImage() before opening mapping dialog" );
		}

	}

}