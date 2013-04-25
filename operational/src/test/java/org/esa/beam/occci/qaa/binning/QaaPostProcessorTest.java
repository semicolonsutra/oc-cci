package org.esa.beam.occci.qaa.binning;

import org.esa.beam.binning.support.VariableContextImpl;
import org.esa.beam.binning.support.VectorImpl;
import org.esa.beam.occci.qaa.QaaConstants;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class QaaPostProcessorTest {

    @Test
    public void testGetOutputFeatureNames() {
        final String[] outputFeatureNames = {"out", "feature", "names"};

        final QaaConfig config = new QaaConfig();
        config.setSensorName(QaaConstants.SEAWIFS);

        final QaaPostProcessor qaaPostProcessor = new QaaPostProcessor(new VariableContextImpl(), config, outputFeatureNames);
        assertArrayEquals(outputFeatureNames, qaaPostProcessor.getOutputFeatureNames());
    }

    @Test
    public void testProcess_MODIS_a_pig_a_total() {
        final float[] rrs_in = {0.0020080009f, 0.0030860009f, 0.0030160008f, 0.0031800009f, 0.0030520008f, 0.0012980009f};
        final VectorImpl outVector = new VectorImpl(rrs_in);

        final QaaConfig config = createConfig();
        config.setA_pig_out_indices(new int[]{0, 1, 2});
        config.setA_total_out_indices(new int[]{2, 3, 4});

        final VariableContextImpl varCtx = createVariableContext();

        final String[] outputFeatureNames = QaaDescriptor.createOutputFeatureNames(config);
        final VectorImpl postVector = new VectorImpl(new float[outputFeatureNames.length]);

        final QaaPostProcessor postProcessor = new QaaPostProcessor(varCtx, config, outputFeatureNames);
        postProcessor.compute(outVector, postVector);

        // out:     [a_pig_412, a_pig_443, a_pig_488, a_total_488, a_total_531, a_total_547]

        // a_pig:   [-0.035466618835926056, -0.04170411825180054, 0.03294333815574646]
        assertEquals(-0.035466618835926056, postVector.get(0), 1e-8);
        assertEquals(-0.04170411825180054, postVector.get(1), 1e-8);
        assertEquals(0.03294333815574646, postVector.get(2), 1e-8);

        // a_total: [0.3258124589920044, 0.18097913265228271, 0.1512765884399414, 0.12253236770629883, 0.12102054804563522]
        assertEquals(0.1512765884399414, postVector.get(3), 1e-8);
        assertEquals(0.12253236770629883, postVector.get(4), 1e-8);
        assertEquals(0.12102054804563522, postVector.get(5), 1e-8);
    }

    @Test
    public void testProcess_MODIS_a_ys_bb_spm() {
        final float[] rrs_in = {0.0020080009f, 0.0030860009f, 0.0030160008f, 0.0031800009f, 0.0030520008f, 0.0012980009f};
        final VectorImpl outVector = new VectorImpl(rrs_in);

        final QaaConfig config = createConfig();
        config.setA_ys_out_indices(new int[]{0, 2});
        config.setBb_spm_out_indices(new int[]{0, 2, 3, 4});

        final VariableContextImpl varCtx = createVariableContext();

        final String[] outputFeatureNames = QaaDescriptor.createOutputFeatureNames(config);
        final VectorImpl postVector = new VectorImpl(new float[outputFeatureNames.length]);

        final QaaPostProcessor postProcessor = new QaaPostProcessor(varCtx, config, outputFeatureNames);
        postProcessor.compute(outVector, postVector);

        // out:     [[a_ys_412, a_ys_488, bb_spm_412 ,bb_spm_488 ,bb_spm_531 ,bb_spm_547]]
        // a_ys:    [0.35672852396965027, 0.21561411023139954, 0.10381654649972916]
        assertEquals(0.35672852396965027, postVector.get(0), 1e-8);
        assertEquals(0.10381654649972916, postVector.get(1), 1e-8);

        // bb_spm:  [0.01384813990443945, 0.011719915084540844, 0.0095791881904006, 0.008171066641807556, 0.00775269977748394]
        assertEquals(0.01384813990443945, postVector.get(2), 1e-8);
        assertEquals(0.0095791881904006, postVector.get(3), 1e-8);
        assertEquals(0.008171066641807556, postVector.get(4), 1e-8);
        assertEquals(0.00775269977748394, postVector.get(5), 1e-8);
    }

    private VariableContextImpl createVariableContext() {
        final VariableContextImpl context = new VariableContextImpl();
        context.defineVariable("ref_1");
        context.defineVariable("ref_2");
        context.defineVariable("ref_3");
        context.defineVariable("ref_4");
        context.defineVariable("ref_5");
        context.defineVariable("ref_6");
        return context;
    }

    private QaaConfig createConfig() {
        final QaaConfig config = new QaaConfig();
        config.setSensorName(QaaConstants.MODIS);
        config.setBandNames(new String[]{"ref_1", "ref_2", "ref_3", "ref_4", "ref_5", "ref_6",});
        return config;
    }
}
