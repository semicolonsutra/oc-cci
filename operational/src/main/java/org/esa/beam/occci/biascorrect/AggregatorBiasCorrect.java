package org.esa.beam.occci.biascorrect;

import org.esa.beam.binning.AbstractAggregator;
import org.esa.beam.binning.Aggregator;
import org.esa.beam.binning.AggregatorConfig;
import org.esa.beam.binning.AggregatorDescriptor;
import org.esa.beam.binning.BinContext;
import org.esa.beam.binning.Observation;
import org.esa.beam.binning.VariableContext;
import org.esa.beam.binning.Vector;
import org.esa.beam.binning.WritableVector;
import org.esa.beam.framework.gpf.annotations.Parameter;

import java.text.DecimalFormat;
import java.text.NumberFormat;

public class AggregatorBiasCorrect extends AbstractAggregator {

    public static final String NAME = "OC-CCI-BIAS";

    private final DateIndexCalculator dateIndexCalculator;
    private final int numFeatures;
    private final int[] varIndexes;
    private final float noDataValue;

    private int dateIdx;

    public AggregatorBiasCorrect(DateIndexCalculator dateIndexCalculator, String[] spatialFeatureNames, String[] temporalFeatureNames, String[] outputFeatureNames, float noDataValue, int[] varIndexes) {
        super(NAME, spatialFeatureNames, temporalFeatureNames, outputFeatureNames);
        this.dateIndexCalculator = dateIndexCalculator;
        this.numFeatures = varIndexes.length;
        this.varIndexes = varIndexes;
        this.noDataValue = noDataValue;

        dateIdx = DateIndexCalculator.INVALID;
    }

    @Override
    public void initSpatial(BinContext ctx, WritableVector vector) {
    }

    @Override
    public void aggregateSpatial(BinContext ctx, Observation observationVector, WritableVector spatialVector) {
        if (dateIdx == DateIndexCalculator.INVALID) {
            final int currentIndex = dateIndexCalculator.get(observationVector.getMJD());
            if (currentIndex != DateIndexCalculator.INVALID) {
                dateIdx = currentIndex;
            }
        }

        for (int i = 0; i < numFeatures; i++) {
            spatialVector.set(i, observationVector.get(varIndexes[i]));
        }
        spatialVector.set(numFeatures, dateIdx);
    }

    @Override
    public void completeSpatial(BinContext ctx, int numSpatialObs, WritableVector spatialVector) {
        // nothing to do here tb 2013-09-18
    }

    @Override
    public void initTemporal(BinContext ctx, WritableVector vector) {
        final int size = vector.size();
        for (int i = 0; i < size; i++) {
            vector.set(i, 0.f);
        }
    }

    @Override
    public void aggregateTemporal(BinContext ctx, Vector spatialVector, int numSpatialObs, WritableVector temporalVector) {
        if (numSpatialObs == 0) {
            return;
        }

        final int indexCount = dateIndexCalculator.getIndexCount();
        final int variablesCount = getSpatialFeatureNames().length - 1;
        for (int i = 0; i < variablesCount; i++) {
            final float value = spatialVector.get(i);
            if (Float.isNaN(value)) {
                continue;
            }

            final int offset = i * 2 * indexCount + (int) spatialVector.get(variablesCount) * 2;
            final float sum = temporalVector.get(offset);
            temporalVector.set(offset, sum + value);
            final float count = temporalVector.get(offset + 1);
            temporalVector.set(offset + 1, count + 1);
        }
    }

    @Override
    public void completeTemporal(BinContext ctx, int numTemporalObs, WritableVector temporalVector) {
        // nothing to do here tb 2013-09-20
    }

    @Override
    public void computeOutput(Vector temporalVector, WritableVector outputVector) {
        final int numYears = dateIndexCalculator.getNumYears();

        for (int band = 0; band < numFeatures; band++) {
            final float[] monthlyMeans = aggregateMonths(temporalVector, numYears, band);
            final float mean = aggregateYear(monthlyMeans, noDataValue);
            outputVector.set(band, mean);
        }
    }


    static float[] aggregateMonths(Vector temporalVector, int numYears, int bandNumber) {
        final float[] monthlyMeans = new float[12];
        final int varOffset = bandNumber * numYears * 12 * 2;

        for (int month = 0; month < 12; month++) {
            double monthSum = 0.f;
            int monthMeasCount = 0;
            for (int year = 0; year < numYears; year++) {
                final int offset = (year * 12 + month) * 2 + varOffset;
                final float monthlySum = temporalVector.get(offset);
                final float monthlyCounts = temporalVector.get(offset + 1);
                if (monthlyCounts > 0.f) {
                    final float monthlyAverage = monthlySum / monthlyCounts;
                    monthSum += monthlyAverage;
                    monthMeasCount++;
                }
            }
            if (monthMeasCount > 0) {
                monthlyMeans[month] = (float) (monthSum / monthMeasCount);
            } else {
                monthlyMeans[month] = Float.NaN;
            }
        }

        return monthlyMeans;
    }

    static float aggregateYear(float[] monthlyMeans, float noDataValue) {
        double sum = 0.0;
        int count = 0;

        for (float monthlyMean : monthlyMeans) {
            if (!Float.isNaN(monthlyMean)) {
                sum += monthlyMean;
                ++count;
            }
        }
        if (count > 0) {
            return (float) (sum / count);
        } else {
            return noDataValue;
        }
    }


    public static class Descriptor implements AggregatorDescriptor {

        @Override
        public Aggregator createAggregator(VariableContext varCtx, AggregatorConfig aggregatorConfig) {
            if (aggregatorConfig instanceof Config) {
                Config config = (Config) aggregatorConfig;
                DateIndexCalculator dateIndexCalculator = createDateIndexCalculator(config);
                String[] spatialFeatureNames = createSpatialFeatureNames(config);
                String[] temporalFeatureNames = createTemporalFeatureNames(config, dateIndexCalculator);
                String[] outputFeatureNames = createOutputFeatureNames(config);
                float noDataValue = config.getNoDataValue();
                String[] varNames = config.getVarNames();

                int[] varIndexes = new int[varNames.length];
                for (int i = 0; i < varNames.length; i++) {
                    int varIndex = varCtx.getVariableIndex(varNames[i]);
                    if (varIndex < 0) {
                        throw new IllegalArgumentException("Input Variable does not exist: " + varNames[i]);
                    }
                    varIndexes[i] = varIndex;
                }

                return new AggregatorBiasCorrect(dateIndexCalculator,
                                                 spatialFeatureNames,
                                                 temporalFeatureNames,
                                                 outputFeatureNames,
                                                 noDataValue,
                                                 varIndexes);
            }
            throw new IllegalArgumentException("Invalid type of configuration: " + aggregatorConfig.getClass());
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public AggregatorConfig createConfig() {
            return new Config();
        }

        // package access for testing only tb 2013-09-18
        static String[] createSpatialFeatureNames(Config config) {
            final String[] varNames = config.getVarNames();

            if (varNames.length == 0) {
                return new String[0];
            }

            final String[] featureNames = new String[varNames.length + 1];
            System.arraycopy(varNames, 0, featureNames, 0, varNames.length);
            featureNames[varNames.length] = "dateIndex";

            return featureNames;
        }

        static String[] createTemporalFeatureNames(Config config, DateIndexCalculator dateIndexCalculator) {
            final NumberFormat numberFormat = new DecimalFormat("000");
            final int numIndices = dateIndexCalculator.getIndexCount();
            final String[] varNames = config.getVarNames();
            final int numFeatures = varNames.length * numIndices * 2;
            final String[] temporalFeatureNames = new String[numFeatures];

            for (int var = 0; var < varNames.length; var++) {
                for (int idx = 0; idx < numIndices; idx++) {
                    final int featureNameOffset = var + 2 * idx;
                    final String offset = numberFormat.format(idx);
                    temporalFeatureNames[featureNameOffset] = varNames[var] + "_" + offset + "_sum";
                    temporalFeatureNames[featureNameOffset + 1] = varNames[var] + "_" + offset + "_count";
                }
            }
            return temporalFeatureNames;
        }

        static String[] createOutputFeatureNames(Config config) {
            final String[] varNames = config.getVarNames();
            final String[] outputFeatureNames = new String[varNames.length];
            for (int i = 0; i < outputFeatureNames.length; i++) {
                outputFeatureNames[i] = varNames[i].concat("_mean");
            }
            return outputFeatureNames;
        }

        static DateIndexCalculator createDateIndexCalculator(Config config) {
            final int startYear = config.getStartYear();
            final int endYear = config.getEndYear();
            if (endYear < startYear) {
                throw new IllegalArgumentException("End year < Start Year");
            }

            return new DateIndexCalculator(startYear, endYear);
        }
    }

    public static class Config extends AggregatorConfig {

        @Parameter
        String[] varNames;

        @Parameter(defaultValue = "2005")
        int startYear;
        @Parameter(defaultValue = "2010")
        int endYear;
        @Parameter(defaultValue = "NaN")
        float noDataValue;

        public Config() {
            super(NAME);
            varNames = new String[0];
            startYear = 2005;
            endYear = 2010;
            noDataValue = Float.NaN;
        }

        @Override
        public String[] getVarNames() {
            return varNames;
        }

        public int getStartYear() {
            return startYear;
        }

        public int getEndYear() {
            return endYear;
        }

        public float getNoDataValue() {
            return noDataValue;
        }
    }
}

