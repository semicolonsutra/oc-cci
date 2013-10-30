package org.esa.beam.occci.merging;

import org.esa.beam.binning.VariableContext;
import org.esa.beam.binning.support.VectorImpl;
import org.esa.beam.occci.qaa.QaaConstants;
import org.esa.beam.occci.qaa.binning.QaaConfig;
import org.esa.beam.occci.util.binning.BinningUtils;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;


public class PostMergingProcessorTest {

    private static final String[] BAND_NAMES = new String[]{"Rrs_412", "Rrs_443", "Rrs_490", "Rrs_510", "Rrs_555", "Rrs_670"};
    private static final int[] ALL_IOPS = new int[]{0, 1, 2, 3, 4, 5};

    @Test
    public void testCompute() throws Exception {
        String[] bands = BinningUtils.combine(BAND_NAMES, "sensor_0", "sensor_1", "sensor_2");
        VariableContext varCtx = BinningUtils.createVariableContext(bands);

        QaaConfig qaaConfig = new QaaConfig();
        qaaConfig.setSensorName(QaaConstants.SEAWIFS);
        qaaConfig.setBandNames(BAND_NAMES);
        qaaConfig.setATotalOutIndices(ALL_IOPS);
        qaaConfig.setBbSpmOutIndices(ALL_IOPS);
        qaaConfig.setAPigOutIndices(ALL_IOPS);
        qaaConfig.setAYsOutIndices(ALL_IOPS);
        PostMergingProcessor processor = new PostMergingProcessor(varCtx, qaaConfig);
        String[] outputFeatureNames = processor.getOutputFeatureNames();
        String[] expectOutputFeatures = {
                "a_pig_412", "a_pig_443", "a_pig_490", "a_pig_510", "a_pig_555", "a_pig_667",
                "a_total_412", "a_total_443", "a_total_490", "a_total_510", "a_total_555", "a_total_667",
                "a_ys_412", "a_ys_443", "a_ys_490", "a_ys_510", "a_ys_555", "a_ys_667",
                "bb_spm_412", "bb_spm_443", "bb_spm_490", "bb_spm_510", "bb_spm_555", "bb_spm_667",
                "chlor_a",
                "Rrs_412", "Rrs_443", "Rrs_490", "Rrs_510", "Rrs_555", "Rrs_670",
                "sensor_0", "sensor_1", "sensor_2",
                "water_class1", "water_class2", "water_class3", "water_class4",
                "water_class5", "water_class6", "water_class7", "water_class8", "water_class9"};
        assertArrayEquals(expectOutputFeatures, outputFeatureNames);

        final float[] rrsPlusSensor = {0.012084f, 0.0089211f, 0.0062153f, 0.0021173f, 0.0014871f, 9.943E-5f, 3f, 5f, 7f};
        VectorImpl input = new VectorImpl(rrsPlusSensor);
        float[] outElems = new float[outputFeatureNames.length];
        VectorImpl output = new VectorImpl(outElems);

        processor.compute(input, output);

        float[] expectedOut = new float[]{
                0.00812609f, 0.010552787f, 0.0054600653f, 0.024190547f, -3.937177E-4f, 0.010300436f,
                0.025696315f, 0.025721889f, 0.024404487f, 0.0595946f, 0.060664434f, 0.44545093f,
                0.013019664f, 0.008099962f, 0.003944421f, 0.002904054f, 0.0014581523f, 2.6249606E-4f,
                0.006295771f, 0.0046820333f, 0.0031252566f, 0.0026683253f, 0.0019186197f, 9.558937E-4f,
                0.07238893f,
                0.012084f, 0.0089211f, 0.0062153f, 0.0021173f, 0.0014871f, 9.943E-5f,
                3.0f, 5.0f, 7.0f,
                5.5386554E-8f, 1.1404647E-9f, 7.483298E-7f, 4.245972E-9f,
                1.001075E-18f, 4.1508E-9f, 1.5326365E-6f, 1.5590174E-10f, 2.346046E-6f};
        assertArrayEquals(expectedOut, outElems, 1e-6f);

    }
}
