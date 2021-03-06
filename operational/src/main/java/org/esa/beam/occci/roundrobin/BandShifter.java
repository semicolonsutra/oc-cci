package org.esa.beam.occci.roundrobin;

import org.esa.beam.occci.bandshift.BandShiftCorrection;
import org.esa.beam.occci.bandshift.CorrectionContext;
import org.esa.beam.occci.bandshift.Sensor;

import java.io.IOException;
import java.util.Arrays;

class BandShifter {

    /**
     * Shifts the incoming spectrum to MERIS bands.
     *
     * @return rrs at {413, 443, 490, 510, 560, 620, 665}
     */
    static double[] toMeris(InSituSpectrum spectrum, double[] qaaAt443) throws IOException {
        final Sensor toMeris = SensorFactory.createToMeris(spectrum);
        final CorrectionContext context = new CorrectionContext(toMeris);
        final BandShiftCorrection bandShiftCorrection = new BandShiftCorrection(context);

        double[] rrs_corrected = bandShift(spectrum, qaaAt443, bandShiftCorrection);
        if (isCorrected(rrs_corrected)) {
            return bandShiftCorrection.weightedAverageEqualCorrectionProducts(rrs_corrected);
        }
        return null;
    }

    /**
     * Shifts the incoming spectrum to MODIS bands.
     *
     * @return rrs at {412, 443, 488, 531, 547, 667, 678}
     */
    static double[] toModis(InSituSpectrum spectrum, double[] qaaAt443) throws IOException {
        final Sensor toModis = SensorFactory.createToModis(spectrum);
        final CorrectionContext context = new CorrectionContext(toModis);
        final BandShiftCorrection bandShiftCorrection = new BandShiftCorrection(context);

        double[] rrs_corrected = bandShift(spectrum, qaaAt443, bandShiftCorrection);
        if (isCorrected(rrs_corrected)) {
            return bandShiftCorrection.weightedAverageEqualCorrectionProducts(rrs_corrected);
        }
        return null;
    }

    /**
     * Shifts the incoming spectrum to SeaWiFS bands.
     *
     * @return rrs at {412, 443, 490, 510, 555, 670}
     */
    static double[] toSeaWifs(InSituSpectrum spectrum, double[] qaaAt443) throws IOException {
        final Sensor toSeaWifs = SensorFactory.createToSeaWifs(spectrum);
        final CorrectionContext context = new CorrectionContext(toSeaWifs);
        final BandShiftCorrection bandShiftCorrection = new BandShiftCorrection(context);

        double[] rrs_corrected = bandShift(spectrum, qaaAt443, bandShiftCorrection);
        if (isCorrected(rrs_corrected)) {
            final double[] correctedAveraged = bandShiftCorrection.weightedAverageEqualCorrectionProducts(rrs_corrected);
            return Arrays.copyOf(correctedAveraged, correctedAveraged.length - 1);
        }
        return null;

    }

    private static double[] bandShift(InSituSpectrum spectrum, double[] qaaAt443, BandShiftCorrection bandShiftCorrection) {
        double[] rrs_corrected = null;
        if (spectrum.isCompleteMeris()) {
            rrs_corrected = correctFromMeris(spectrum, qaaAt443, bandShiftCorrection);
        } else if (spectrum.isCompleteModis()) {
            rrs_corrected = correctFromModis(spectrum, qaaAt443, bandShiftCorrection);
        } else if (spectrum.isCompleteSeaWiFS()) {
            rrs_corrected = correctFromSeaWifs(spectrum, qaaAt443, bandShiftCorrection);
        } else if (spectrum.isCompleteQaa()) {
            rrs_corrected = correctFromQaa(spectrum, qaaAt443, bandShiftCorrection);
        }
        return rrs_corrected;
    }

    private static double[] correctFromModis(InSituSpectrum spectrum, double[] qaaAt443, BandShiftCorrection bandShiftCorrection) {
        return bandShiftCorrection.correctBandshift(spectrum.getModisMeasurements(), spectrum.getModisWavelengths(), qaaAt443);
    }

    private static double[] correctFromMeris(InSituSpectrum spectrum, double[] qaaAt443, BandShiftCorrection bandShiftCorrection) {
        return bandShiftCorrection.correctBandshift(spectrum.getMerisMeasurements(), spectrum.getMerisWavelengths(), qaaAt443);
    }

    private static double[] correctFromSeaWifs(InSituSpectrum spectrum, double[] qaaAt443, BandShiftCorrection bandShiftCorrection) {
        return bandShiftCorrection.correctBandshift(spectrum.getSeaWifsMeasurements(), spectrum.getSeaWifsWavelengths(), qaaAt443);
    }

    private static double[] correctFromQaa(InSituSpectrum spectrum, double[] qaaAt443, BandShiftCorrection bandShiftCorrection) {
        return bandShiftCorrection.correctBandshift(spectrum.getQaaMeasurements(), spectrum.getQaaWavelengths(), qaaAt443);
    }

    public static boolean isCorrected(double[] rrs_corrected) {
        return rrs_corrected != null && rrs_corrected.length > 0;
    }
}
