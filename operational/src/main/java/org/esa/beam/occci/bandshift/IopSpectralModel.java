/*
 * Copyright (C) 2013 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.occci.bandshift;

import java.util.HashMap;
import java.util.Map;

/**
 * Procedure and related utility to predict IOPs at given wavelengths starting
 * from known values at other wavelengths, by applying a spectral model.
 * The IOPs taken into account are pythoplankton absorption aph,
 * detritus+gelbstoff absorption adg and particle backscattering bbp.
 *
 * @author Frédéric Mélin (European Commission/JRC/IES/WRES)
 * @author Gert Sclep (European Commission/JRC/IES/WRES)
 * @author MarcoZ
 */
public class IopSpectralModel {

    private static final Map<String, Double> BRICAUD_A;
    private static final Map<String, Double> BRICAUD_B;

    static {
        BRICAUD_A = new HashMap<String, Double>();
        BRICAUD_A.put("411", 0.0318);
        BRICAUD_A.put("412", 0.0323);
        BRICAUD_A.put("413", 0.032775);
        BRICAUD_A.put("441", 0.04005);
        BRICAUD_A.put("442", 0.0398);
        BRICAUD_A.put("443", 0.0394);
        BRICAUD_A.put("488", 0.028); // was 0.0279
        BRICAUD_A.put("489", 0.02765);
        BRICAUD_A.put("490", 0.027); // was 0.0274
        BRICAUD_A.put("491", 0.02705);
        BRICAUD_A.put("492", 0.0267);

        BRICAUD_A.put("510", 0.0180);
        BRICAUD_A.put("530", 0.0117);
        BRICAUD_A.put("531", 0.012); // was 0.0115
        BRICAUD_A.put("547", 0.00845);
        BRICAUD_A.put("551", 0.0078);
        BRICAUD_A.put("552", 0.0076);
        BRICAUD_A.put("555", 0.007);
        BRICAUD_A.put("557", 0.00665); // was 0.0066
        BRICAUD_A.put("558", 0.0065);
        BRICAUD_A.put("560", 0.006); // was 0.0062

        BRICAUD_A.put("617", 0.00625);
        BRICAUD_A.put("618", 0.0063);
        BRICAUD_A.put("620", 0.0065);
        BRICAUD_A.put("662", 0.0129);
        BRICAUD_A.put("665", 0.015); // was 0.0152
        BRICAUD_A.put("667", 0.01685);
        BRICAUD_A.put("668", 0.0176);
        BRICAUD_A.put("669", 0.01825);
        BRICAUD_A.put("670", 0.019); // was 0.0189
        BRICAUD_A.put("675", 0.0201);
        BRICAUD_A.put("678", 0.0193);

        BRICAUD_B = new HashMap<String, Double>();
        BRICAUD_B.put("411", 0.2845);
        BRICAUD_B.put("412", 0.286);
        BRICAUD_B.put("413", 0.28775);
        BRICAUD_B.put("441", 0.3355);
        BRICAUD_B.put("442", 0.339);
        BRICAUD_B.put("443", 0.3435);
        BRICAUD_B.put("488", 0.369);
        BRICAUD_B.put("489", 0.365);
        BRICAUD_B.put("490", 0.361);
        BRICAUD_B.put("491", 0.3585);
        BRICAUD_B.put("492", 0.356);

        BRICAUD_B.put("510", 0.260);
        BRICAUD_B.put("530", 0.139);
        BRICAUD_B.put("531", 0.134);
        BRICAUD_B.put("547", 0.0625);
        BRICAUD_B.put("551", 0.048);
        BRICAUD_B.put("552", 0.044);
        BRICAUD_B.put("555", 0.032); // was 0.0.315
        BRICAUD_B.put("557", 0.0215); // was  0.0182
        BRICAUD_B.put("558", 0.016);
        BRICAUD_B.put("560", 0.016);

        BRICAUD_B.put("617", 0.0635);
        BRICAUD_B.put("618", 0.064);
        BRICAUD_B.put("620", 0.064);
        BRICAUD_B.put("662", 0.125);
        BRICAUD_B.put("665", 0.134);
        BRICAUD_B.put("667", 0.140);
        BRICAUD_B.put("668", 0.143);
        BRICAUD_B.put("669", 0.146);
        BRICAUD_B.put("670", 0.149);
        BRICAUD_B.put("675", 0.158);
        BRICAUD_B.put("678", 0.158);
    }

    /**
     * Retrieves the A and B Bricaud coefficient for given wavelengths.
     */
    public static double[] getABBricaud(double wl) {
        // a and b at 443 derived from values previously in a_bb_prediction
        // a_443 = 0.0394 and k (= 1/(1-b))= 1.52323
        final String wavelengthAsString = Integer.toString((int) wl);
//        System.out.println("wavelengthAsString = " + wavelengthAsString);
        final double aBricaud = BRICAUD_A.get(wavelengthAsString);
        final double bBricaud = BRICAUD_B.get(wavelengthAsString);
        return new double[]{aBricaud, bBricaud};
    }

    /**
     * Using a spectral model, IOPs at a given wavelength are converted to a set of desired wavelengths.
     * This is done for phytoplankton absorption, for detritus-gelbstoff absorption and for particle backscattering.
     * The phytoplankton absorption is converted using the Bricaud formula (see Bricaud et al. Variability in the chlorophyll-specific absorption coefficients
     * of natural phytoplankton: Analysis and parameterization. Journal of Geophysical Research. 1995;100(95):321–332), and this requires the Bricaud
     * coefficients A and B to be given for both the input as for the output wavelengths. The detritus-gelbstoff and particle backscattering
     * are spectrally evolved using the same spectral slope as used in QAAv5 (See Z. Lee, B. Lubac, J. Werdell, R. Arnone: An update of the quasi-analytical algorithm
     * (QAA_v5): International Ocean Color Group software report). To calculate these slopes, the below water remote sensing reflectances in the blue and green band
     * need to be given as input.
     * <p/>
     *
     * @param lambda_in: in, required, type="float/fltarr(m)"
     *                   start wavelength(s) of the spectral model, in general the start wavelength used is the same for each of the individual conversions
     *                   <p/>
     *                   a_in: in, required, type="float/fltarr(m)"
     *                   Bricaud A coefficient for the wavelengths given in `lambda_in`
     *                   <p/>
     *                   b_in: in, required, type="float/fltarr(m)"
     *                   Bricaud B coefficient for the wavelengths given in `lambda_in`
     *                   <p/>
     *                   aph_in: in, required, type="float/fltarr(m)/fltarr(m,n)"
     *                   phytoplankton absorption for the wavelengths given in `lambda_in` (column dimension m), for 1 or more records (if >1: row dimension n)
     *                   <p/>
     *                   adg_in: in, required, type="float/fltarr(m)/fltarr(m,n)"
     *                   CDOM+detritus absorption for the wavelengths given in `lambda_in` (column dimension m), for 1 or more records (if >1: row dimension n)
     *                   <p/>
     *                   bbp_in: in, required, type="float/fltarr(m)/fltarr(m,n)"
     *                   particulate back-scattering for the wavelengths given in `lambda_in` (column dimension m), for 1 or more records (if >1: row dimension n)
     *                   <p/>
     *                   rrs_blue_in: in, required, type="float/fltarr(n)"
     *                   below water remote sensing reflection in the blue band (443 nm), for 1 or more records (if >1: column dimension n)
     *                   <p/>
     *                   rrs_green_in: in, required, type="float/fltarr(n)"
     *                   below water remote sensing reflection in the green band (547 or 555 or 560 nm, depending on sensor used), for 1 or more records (if >1: column dimension n)
     *                   <p/>
     *                   lambda_out: in, required, type="float/fltarr(m)"
     *                   end wavelengths to which `aph_in`, `adg_in` and `bbp_in` should be evolved towards
     *                   <p/>
     *                   the dimension m is equal to the dimension m of `lambda_in`
     *                   <p/>
     *                   a_out: in, required, type="float/fltarr(m)"
     *                   Bricaud A coefficient for the wavelengths given in `lambda_out`
     *                   <p/>
     *                   b_out: in, required, type="float/fltarr(m)"
     *                   Bricaud B coefficient for the wavelengths given in `lambda_out`
     * @return array of
     *         aph_out: out, type="float/fltarr(m)/fltarr(m,n)"
     *         phytoplankton absorption for the wavelengths given in `lambda_out` (column dimension m), for 1 or more records (if >1: row dimension n), obtained using Bricaud formula starting from values in `aph_in`
     *         <p/>
     *         adg_out: out, type="float/fltarr(m)/fltarr(m,n)"
     *         CDOM+detritus absorption for the wavelengths given in `lambda_out` (column dimension m), for 1 or more records (if >1: row dimension n), obtained applying QAAv5 spectral slopes starting from values in `adg_in`.
     *         <p/>
     *         bbp_out: out, type="float/fltarr(m)/fltarr(m,n)"
     *         particulate back-scattering for the wavelengths given in `lambda_out` (column dimension m), for 1 or more records (if >1: row dimension n), obtained applying QAAv5 spectral slopes starting from value in `bbp_in`
     */
    public static double[] iopSpectralModel(double lambda_in, double a_in, double b_in, double aph_in, double adg_in, double bbp_in, double rrs_blue_in, double rrs_green_in,
                                            double lambda_out, double a_out, double b_out) {

//        double k = 1.52323; //  = 1/(1-b_443)
//        double a_443 = 0.0394;

        double S = 0.015;
        double rat = rrs_blue_in / rrs_green_in;
        double sdg = S + 0.002 / (0.6 + rat);

        double yy = 2.0 * (1.0 - 1.2 * Math.exp(-0.9 * rat));

        // Chla concentration; Bricaud et al. (1995)
        double Chla = Math.pow((aph_in / a_in), (1 / (1 - b_in)));

        //IOPs for input wavelengths
        double ll = lambda_out - lambda_in;
        double aph_out = a_out * Math.pow(Chla, (1 - b_out));
        double adg_out = adg_in * Math.exp(-sdg * ll);
        double bbp_out = bbp_in * Math.pow((lambda_in / lambda_out), yy);

        return new double[]{aph_out, adg_out, bbp_out};
    }
}
