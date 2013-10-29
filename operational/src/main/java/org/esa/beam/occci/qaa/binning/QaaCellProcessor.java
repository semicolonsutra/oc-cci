package org.esa.beam.occci.qaa.binning;


import org.esa.beam.binning.CellProcessor;
import org.esa.beam.binning.VariableContext;
import org.esa.beam.binning.Vector;
import org.esa.beam.binning.WritableVector;
import org.esa.beam.occci.qaa.*;
import org.esa.beam.occci.util.binning.BinningUtils;

public class QaaCellProcessor extends CellProcessor {

    private final QaaAlgorithm qaaAlgorithm;
    private final int[] bandIndices;
    private final float[] rrs;
    QaaResult qaaResult;
    private final ResultMapper resultMapper;

    public QaaCellProcessor(VariableContext varCtx, QaaConfig config, String[] outputFeatureNames) {
        super(outputFeatureNames);

        final SensorConfig sensorConfig = SensorConfigFactory.get(config.getSensorName());
        qaaAlgorithm = new QaaAlgorithm(sensorConfig);

        final String[] bandNames = config.getBandNames();
        bandIndices = BinningUtils.getBandIndices(varCtx, bandNames);

        rrs = new float[bandNames.length];
        qaaResult = new QaaResult();
        resultMapper = new ResultMapper(config);
    }

    @Override
    public void compute(Vector inputVector, WritableVector outputVector) {
        for (int i = 0; i < rrs.length; i++) {
            rrs[i] = inputVector.get(bandIndices[i]);
        }

        try {
            qaaResult = qaaAlgorithm.process(rrs, qaaResult);
        } catch (ImaginaryNumberException e) {
            BinningUtils.setToInvalid(outputVector);
            return;
        }
        if (containsInfinite(qaaResult.getA_PIG()) ||
                containsInfinite(qaaResult.getA_Total()) ||
                containsInfinite(qaaResult.getA_YS()) ||
                containsInfinite(qaaResult.getBB_SPM())) {
            BinningUtils.setToInvalid(outputVector);
            return;
        }


        resultMapper.assign(qaaResult, rrs, outputVector);
    }

    private static boolean containsInfinite(float[] values) {
        for (float value : values) {
            if (Float.isInfinite(value)) {
                return true;
            }
        }
        return false;
    }
}