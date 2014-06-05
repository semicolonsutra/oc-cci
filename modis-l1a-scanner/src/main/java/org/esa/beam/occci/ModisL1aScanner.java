/*
 * Copyright (C) 2014 Brockmann Consult GmbH (info@brockmann-consult.de)
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

import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import ucar.ma2.Array;
import ucar.ma2.ArrayChar;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import ucar.nc2.iosp.IOServiceProvider;
import ucar.nc2.iosp.hdf4.H4iosp;
import ucar.nc2.iosp.hdf4.ODLparser;
import ucar.unidata.io.RandomAccessFile;
import ucar.unidata.io.UncompressInputStream;
import ucar.unidata.io.bzip2.CBZip2InputStream;

import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts start-time,ned-time and bounding polygon from a MODIS L1A file
 */
public class ModisL1aScanner {

    static final String CORE_METADATA = "CoreMetadata";
    private static final int BUFFER_SIZE = 100000;
    private static File tmpDir;


    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ModisL1aScanner <FILES>");
            System.exit(1);
        }
        tmpDir = new File("ModisL1aScanner-tmp");
        if (tmpDir.exists() && tmpDir.isFile()) {
            throw new IOException("Temp directory id a file: " + tmpDir.getAbsolutePath());
        }
        if (!tmpDir.exists()) {
            boolean success = tmpDir.mkdir();
            if (!success) {
                throw new IOException("Failed to crate Temp directory " + tmpDir.getAbsolutePath());
            }
        }
        for (String filename : args) {
            long t0 = System.currentTimeMillis();
            try {
//                processSingleFileWithCopying(filename);
                processSingleFileInMemory(filename);
            } catch (IOException ignore) {
                System.err.println("Error while processing:" + filename);
                ignore.printStackTrace();
            } finally {
                long t1 = System.currentTimeMillis();
                long delta = t1 - t0;
                //System.err.println("delta = " + delta);
            }
        }
        boolean sucess = tmpDir.delete();
        if (!sucess) {
            System.err.println("Failed to delete temp directory: " + tmpDir.getPath());
        }
    }

    private static void processSingleFileInMemory(String arg) throws IOException {
        File modisL1aFile = new File(arg);
        String absolutePath = modisL1aFile.getAbsolutePath();
        System.err.println("processing absolutePath = " + absolutePath);
        if (!modisL1aFile.exists()) {
            System.err.println("File does not exist: " + absolutePath);
            return;
        }
        RandomAccessFile inMemoryRaf = getInMemoryRaf(modisL1aFile);
        IOServiceProvider h4iosp = new H4iosp();
        if (h4iosp.isValidFile(inMemoryRaf)) {
            NetcdfFile netcdfFile = new DummyNetcdfFile(h4iosp, inMemoryRaf, modisL1aFile.getAbsolutePath());
            try {
                analyzeFile(absolutePath, netcdfFile);
            } finally {
                netcdfFile.close();
            }
        } else {
            h4iosp.close();
        }
    }

    private static void processSingleFileWithCopying(String arg) throws IOException {
        File modisL1aFile = new File(arg);
        String absolutePath = modisL1aFile.getAbsolutePath();
        System.err.println("processing absolutePath = " + absolutePath);
        if (!modisL1aFile.exists()) {
            System.err.println("File does not exist: " + absolutePath);
            return;
        }
        String uncompressedPath = makeUncompressed(modisL1aFile);
        try {
            NetcdfFile netcdfFile;
            if (uncompressedPath != null) {
                // open uncompressed file
                netcdfFile = NetcdfFile.open(uncompressedPath);
            } else {
                // normal case - not compressed
                netcdfFile = NetcdfFile.open(absolutePath);
            }
            analyzeFile(absolutePath, netcdfFile);
        } finally {
            if (uncompressedPath != null) {
                File uncompressedFile = new File(uncompressedPath);
                boolean sucess = uncompressedFile.delete();
                if (!sucess) {
                    System.err.println("Failed to delete uncompressed file: " + uncompressedPath);
                }
            }
        }
    }

    private static void analyzeFile(String absolutePath, NetcdfFile netcdfFile) throws IOException {
        //System.out.println("netcdfFile = " + netcdfFile);
        Group rootGroup = netcdfFile.getRootGroup();
        Element coreElem = getEosElement(CORE_METADATA, rootGroup);
        //printElement(coreElem);

        Element inventoryMetadata = coreElem.getChild("INVENTORYMETADATA");
        Element masterGroup = inventoryMetadata.getChild("MASTERGROUP");
        Element rangeDateTime = masterGroup.getChild("RANGEDATETIME");

        String startTime = getIsoDateTime(rangeDateTime, "RANGEBEGINNINGDATE", "RANGEBEGINNINGTIME");
        String endTime = getIsoDateTime(rangeDateTime, "RANGEENDINGDATE", "RANGEENDINGTIME");

        Element spatialDomainContainer = masterGroup.getChild("SPATIALDOMAINCONTAINER");
        Element horizontalSpatialComainContainer = spatialDomainContainer.getChild("HORIZONTALSPATIALDOMAINCONTAINER");
        Element gPolygon = horizontalSpatialComainContainer.getChild("GPOLYGON");
        Element gPolygonContainer = gPolygon.getChild("GPOLYGONCONTAINER");
        String polygon = getBoundingPolygon(gPolygonContainer);

        System.out.println(absolutePath + "\t" + startTime + "\t" + endTime + "\t" + polygon);
    }

    private static void printElement(Element element) {
        XMLOutputter fmt = new XMLOutputter(Format.getPrettyFormat());
        try {
            fmt.output(element, System.out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getBoundingPolygon(Element gPolygonContainer) {
        Element gRingPoint = gPolygonContainer.getChild("GRINGPOINT");
        double[] lonValues = getValues(gRingPoint.getChild("GRINGPOINTLONGITUDE"));
        double[] latValues = getValues(gRingPoint.getChild("GRINGPOINTLATITUDE"));
        double[] sequenceNo = getValues(gRingPoint.getChild("GRINGPOINTSEQUENCENO"));
        StringBuilder sb = new StringBuilder();
        sb.append("POLYGON((");
        for (double aSequenceNo : sequenceNo) {
            int sequenceIndex = ((int) aSequenceNo) - 1;
            sb.append(lonValues[sequenceIndex]).append(" ").append(latValues[sequenceIndex]).append(",");
        }
        int sequenceIndex = ((int) sequenceNo[0]) - 1;
        sb.append(lonValues[sequenceIndex]).append(" ").append(latValues[sequenceIndex]);
        sb.append("))");
        return sb.toString();
    }

    private static double[] getValues(Element element) {
        int numValues = Integer.parseInt(element.getChildTextTrim("NUM_VAL"));
        Element valueElement = element.getChild("VALUE");
        List<Element> children = valueElement.getChildren();
        if (children.size() != numValues) {
            String msg = String.format("Error in parsing GRINGPOINT. %d childs, but 'num_val' is %d", children.size(), numValues);
            throw new IllegalArgumentException(msg);
        }
        double[] values = new double[numValues];
        for (int i = 0; i < values.length; i++) {
            values[i] = Double.parseDouble(children.get(i).getTextTrim());
        }
        return values;
    }

    static String getIsoDateTime(Element rangeDateTimeElem, String dateName, String timeName) {
        String date = rangeDateTimeElem.getChild(dateName).getChildTextTrim("VALUE");
        String time = rangeDateTimeElem.getChild(timeName).getChildTextTrim("VALUE");
        return date + "T" + time;
    }

    static Element getEosElement(String name, Group eosGroup) throws IOException {
        String smeta = getEosMetadata(name, eosGroup);
        if (smeta == null) {
            return null;
        }
        smeta = smeta.replaceAll("\\s+=\\s+", "=");
        smeta = smeta.replaceAll("\\?", "_"); // XML names cannot contain the character "?".

        StringBuilder sb = new StringBuilder(smeta.length());
        StringTokenizer lineFinder = new StringTokenizer(smeta, "\t\n\r\f");
        while (lineFinder.hasMoreTokens()) {
            String line = lineFinder.nextToken().trim();
            sb.append(line);
            sb.append("\n");
        }

        ODLparser parser = new ODLparser();
        return parser.parseFromString(sb.toString());// now we have the ODL in JDOM elements
    }

    private static String getEosMetadata(String name, Group eosGroup) throws IOException {
        StringBuilder sbuff = null;
        String structMetadata = null;

        int n = 0;
        while (true) {
            Variable structMetadataVar = eosGroup.findVariable(name + "." + n);
            if (structMetadataVar == null) {
                break;
            }
            if ((structMetadata != null) && (sbuff == null)) { // more than 1 StructMetadata
                sbuff = new StringBuilder(64000);
                sbuff.append(structMetadata);
            }

            Array metadataArray = structMetadataVar.read();
            structMetadata = ((ArrayChar) metadataArray).getString(); // common case only StructMetadata.0, avoid extra copy

            if (sbuff != null) {
                sbuff.append(structMetadata);
            }
            n++;
        }
        return (sbuff != null) ? sbuff.toString() : structMetadata;
    }

    static private String makeUncompressed(File inputFile) throws IOException {
        // see if its a compressed file
        String filename = inputFile.getName();
        int pos = filename.lastIndexOf('.');
        if (pos < 0) {
            return null;
        }
        String suffix = filename.substring(pos + 1);
        String uncompressedFilename = filename.substring(0, pos);

        if (!suffix.equalsIgnoreCase("gzip") && !suffix.equalsIgnoreCase("gz") && !suffix.equalsIgnoreCase("bz2")) {
            return null;
        }

        // ok gonna write it uncompressed
        File uncompressedFile = new File(tmpDir, uncompressedFilename);

        InputStream in = null;
        FileOutputStream fout = new FileOutputStream(uncompressedFile);
        try {
            final InputStream inputStream = new BufferedInputStream(new FileInputStream(inputFile), BUFFER_SIZE);
            if (suffix.equalsIgnoreCase("bz2")) {
                in = new CBZip2InputStream(inputStream, true);
                copy(in, fout, BUFFER_SIZE);
                System.err.println("unbzipped " + inputFile.getAbsolutePath() + " to " + uncompressedFile.getAbsolutePath());
            } else if (suffix.equalsIgnoreCase("gzip") || suffix.equalsIgnoreCase("gz")) {
                in = new GZIPInputStream(inputStream);
                copy(in, fout, BUFFER_SIZE);
                System.err.println("ungzipped " + inputFile.getAbsolutePath() + " to " + uncompressedFile.getAbsolutePath());
            }
        } catch (IOException e) {
            // appears we have to close before we can delete
            fout.close();
            fout = null;

            // dont leave bad files around
            if (uncompressedFile.exists()) {
                if (!uncompressedFile.delete()) {
                    System.err.println("failed to delete uncompressed file (IOException)" + uncompressedFile);
                }
            }
            throw e;

        } finally {
            if (in != null) {
                in.close();
            }
            if (fout != null) {
                fout.close();
            }
        }
        return uncompressedFile.getPath();
    }

    // copied from ucar.nc2.NetcdfFile
    static private void copy(InputStream in, OutputStream out, int bufferSize) throws IOException {
        long t1 = System.currentTimeMillis();
        byte[] buffer = new byte[bufferSize];
        while (true) {
            int bytesRead = in.read(buffer);
            if (bytesRead == -1) {
                break;
            }
            out.write(buffer, 0, bytesRead);
        }
        long t2 = System.currentTimeMillis();
        System.err.println("uncompress-time = " + (t2 - t1));
    }

    private static RandomAccessFile getInMemoryRaf(File file) throws IOException {
        MemoryCacheImageInputStream imageInputStream = new MemoryCacheImageInputStream(openInputStream(file));
        return new ImageInputStreamRandomAccessFile(imageInputStream, file.length());
    }

    private static InputStream openInputStream(File file) throws IOException {
        String fileName = file.getName().toLowerCase();
        InputStream is = new BufferedInputStream(new FileInputStream(file));
        if (fileName.endsWith(".z")) {
            is = new UncompressInputStream(is);
        } else if (fileName.endsWith(".zip")) {
            ZipInputStream zin = new ZipInputStream(is);
            ZipEntry ze = zin.getNextEntry();
            if (ze != null) {
                is = zin;
            }
        } else if (fileName.endsWith(".bz2")) {
            is = new CBZip2InputStream(is, true);
        } else if (fileName.endsWith(".gzip") || fileName.endsWith(".gz")) {
            is = new GZIPInputStream(is);
        }
        return is;
    }

    // use internal class to defer execution of static initializer
    private static class DummyNetcdfFile extends NetcdfFile {

        private DummyNetcdfFile(IOServiceProvider spi, RandomAccessFile raf, String location) throws IOException {
            super(spi, raf, location, null);
        }
    }

    private static class ImageInputStreamRandomAccessFile extends RandomAccessFile {

        private final ImageInputStream imageInputStream;
        private final long length;

        public ImageInputStreamRandomAccessFile(ImageInputStream imageInputStream, long length) {
            super(16000);
            this.imageInputStream = imageInputStream;
            this.length = length;
        }

        @Override
        public String getLocation() {
            return "ImageInputStream";
        }

        @Override
        public long length() throws IOException {
            return length;
        }

        @Override
        protected int read_(long pos, byte[] b, int offset, int len) throws IOException {
            imageInputStream.seek(pos);
            return imageInputStream.read(b, offset, len);
        }

        @Override
        public long readToByteChannel(WritableByteChannel dest, long offset, long nbytes) throws IOException {
            int n = (int) nbytes;
            byte[] buff = new byte[n];
            int done = read_(offset, buff, 0, n);
            dest.write(ByteBuffer.wrap(buff));
            return done;
        }

        @Override
        public void close() throws IOException {
            imageInputStream.close();
        }
    }
}
