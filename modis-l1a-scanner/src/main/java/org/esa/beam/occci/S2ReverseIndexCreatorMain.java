/*
 * Copyright (C) 2015 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.beam.occci;

import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2CellUnion;
import me.lemire.integercompression.ByteIntegerCODEC;
import me.lemire.integercompression.IntWrapper;
import me.lemire.integercompression.differential.IntegratedBinaryPacking;
import me.lemire.integercompression.differential.IntegratedComposition;
import me.lemire.integercompression.differential.IntegratedIntegerCODEC;
import me.lemire.integercompression.differential.IntegratedVariableByte;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by marcoz on 18.08.15.
 */
public class S2ReverseIndexCreatorMain {

    static final short MASK_SHIFT = 2 * S2CellId.MAX_LEVEL - 1;

    public static void main(String[] args) throws IOException, ParseException {
        if (args.length != 2) {
            printUsage();
        }
        File productListFile = new File(args[0]);

        if (!productListFile.exists()) {
            System.err.printf("productList file '%s' does not exits%n", args[0]);
            printUsage();
        }
        List<EoProduct> eoProductList = ProductDB.readProducts("s2", productListFile);
        Collections.sort(eoProductList, ProductDB.EO_PRODUCT_COMPARATOR);
//        List<EoProduct> eoProductList2 = new ArrayList<>();
//        eoProductList2.add(eoProductList.get(365));
//        eoProductList = eoProductList2;


        File indexFile = new File(args[1]);
        File urlFile = new File(indexFile + ".url");
        File poylFile = new File(indexFile + ".polygon");
        File coverFile = new File(indexFile + ".coverages");


        Map<Integer, List<Integer>> reverseIndex = new HashMap<>();
        try (
                DataOutputStream dosIndex = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));
                DataOutputStream dosUrl = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(urlFile)));
                DataOutputStream dosPoly = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(poylFile)))
        ) {
            int productCounter = 0;
            dosIndex.writeInt(eoProductList.size());
            for (EoProduct eoProduct : eoProductList) {
                eoProduct.createGeo();
                S2EoProduct s2EoProduct = (S2EoProduct) eoProduct;

                dosUrl.writeUTF(eoProduct.getName());

                dosIndex.writeLong(eoProduct.getStartTime());
                dosIndex.writeLong(eoProduct.getEndTime());

                S2CellUnion cellUnion = s2EoProduct.cellUnion;
                S2IntCoverage s2IntCoverage = new S2IntCoverage(cellUnion);
                for (int cellId : s2IntCoverage.intIds) {
                    List<Integer> productIndices = reverseIndex.get(cellId);
                    if (productIndices == null) {
                        productIndices = new ArrayList<>();
                        reverseIndex.put(cellId, productIndices);
                    }
                    productIndices.add(productCounter);
                }
                s2EoProduct.writePolygone(dosPoly);

                s2EoProduct.reset();

                productCounter++;
                if (productCounter % 10000 == 0) {
                    System.out.println("counter = " + productCounter);
                }
            }
        }
        System.out.println("reverseIndex.size() = " + reverseIndex.size());
        try (
                DataOutputStream dosCover = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(coverFile)))
        ) {
            dosCover.writeInt(reverseIndex.size());

            IntegratedIntegerCODEC codec =  new IntegratedComposition(
                    new IntegratedBinaryPacking(),
                    new IntegratedVariableByte()
            );

            ArrayList<Integer> keys = new ArrayList<>(reverseIndex.keySet());
            Collections.sort(keys);
            System.out.println("keys.size() = " + keys.size());
            for (Integer key : keys) {
                dosCover.writeInt(key);
                List<Integer> integerList = reverseIndex.get(key);

                int[] data = new int[integerList.size()];
//                System.out.println("Compressing "+data.length+" integers in one go");
                // data should be sorted for best results
                for(int i = 0; i < data.length; ++i) {
                    data[i] = integerList.get(i);
                }
                int [] compressed = new int[data.length+1024];
                IntWrapper inputoffset = new IntWrapper(0);
                IntWrapper outputoffset = new IntWrapper(0);
                codec.compress(data, inputoffset, data.length, compressed, outputoffset);
//                System.out.println("compressed from "+data.length*4/1024+"KB to "+outputoffset.intValue()*4/1024+"KB");

                compressed = Arrays.copyOf(compressed, outputoffset.intValue());

                dosCover.writeInt(data.length);
                dosCover.writeInt(compressed.length);
                for (int intId : compressed) {
                    dosCover.writeInt(intId);
                }

                int[] restored = new int[data.length];
                codec.uncompress(compressed, new IntWrapper(0), compressed.length, restored, new IntWrapper(0));

                if(!Arrays.equals(data, restored))
                         throw new RuntimeException("bug");

            }
        }
    }

    static class S2IntCoverage {
        public final int[] intIds;

        public S2IntCoverage(S2CellUnion cellUnion) {
            List<Integer> intIdList = new ArrayList<>();
            for (S2CellId s2CellId : cellUnion.cellIds()) {
                if (s2CellId.level() < 3) {
                    for (S2CellId c = s2CellId.childBegin(3); !c.equals(s2CellId.childEnd(3)); c = c.next()) {
                        intIdList.add(S2CellIdInteger.asInt(c));
                    }
                } else {
                    intIdList.add(S2CellIdInteger.asInt(s2CellId));
                }
            }
            intIds = new int[intIdList.size()];
            for (int i = 0; i < intIds.length; i++) {
                intIds[i] = intIdList.get(i);
            }
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof S2IntCoverage)) return false;
            return Arrays.equals(intIds, ((S2IntCoverage) other).intIds);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(intIds);
        }
    }


    private static void printUsage() {
        System.err.println("Usage: S2IndexCreatorMain <productList> <indexfile>");
        System.exit(1);
    }

}
