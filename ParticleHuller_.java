import ij.plugin.filter.PlugInFilter;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.*;
import java.io.*;
import java.lang.Float;
import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.process.*;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.Filler;
import ij.plugin.Duplicator;
import ij.measure.*;
 

/**

  */

public class ParticleHuller_ implements PlugInFilter, Measurements  {

    ImagePlus   imp;
    Calibration cal;
    static int minSize = 0;
    static int maxSize = 999999;
    static boolean skipDialogue = false;

    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        cal = imp.getCalibration();
        if (IJ.versionLessThan("1.17y"))
            return DONE;
        else
            return DOES_8G+NO_CHANGES;
    }

    public static void setProperty (String arg1, String arg2) {
        if (arg1.equals("minSize"))
            minSize = Integer.parseInt(arg2);
        else if (arg1.equals("maxSize"))
            maxSize = Integer.parseInt(arg2);
    }

    public void run(ImageProcessor ip) {
        if (!skipDialogue) {
            String unit = cal.getUnit();
            GenericDialog gd = new GenericDialog("Particle Huller");
            gd.addNumericField("Minimum Object Size", minSize, 0, 6, unit);
            gd.addNumericField("Maximum Object Size", maxSize, 0, 6, unit);
            gd.showDialog();
            if (gd.wasCanceled())
                return;

            minSize = (int)gd.getNextNumber();
            maxSize = (int)gd.getNextNumber();
        }
        hull(imp, minSize, maxSize);
    }

    public void hull(ImagePlus imp, int minSize, int maxSize){
        int nFrames = imp.getStackSize();
        ImageProcessor ip = imp.getProcessor(); 

        // This is an array of lists: each index holds a list
        // of hulls in the frame at that index.
        List[] hulls = new List[nFrames];

        // Get real-world unit label (if it is available)
        String unit = cal.getUnit();

        ImageStack stack = imp.getStack();
        int options = 0; // set all ParticleAnalyzer options false

        // Find centroid, area
        int measurements = CENTROID+AREA;

        // Initialize results table
        ResultsTable rt = new ResultsTable();
        rt.reset();

        // iterate over each frame
        for (int iFrame=1; iFrame<=nFrames; iFrame++) {
            rt.reset();

            // Run the particle analysis with the measurements we want (see above).
            ParticleAnalyzer pa = new ParticleAnalyzer(options, measurements, rt, minSize, maxSize);
            pa.analyze(imp, stack.getProcessor(iFrame));

            float[] xCentroids = rt.getColumn(ResultsTable.X_CENTROID);              
            float[] yCentroids = rt.getColumn(ResultsTable.Y_CENTROID);

            if (xCentroids == null)
                continue;

            Wand wand = new Wand(ip);

            hulls[iFrame - 1] = new ArrayList();

            // loop over each particle in this frame
            for (int particleIndex = 0; particleIndex < xCentroids.length; particleIndex++) {
                // Fields populated by Wand
                int[] xPoints, yPoints;
                int nPoints;

                // Use the wand at each centroid to select the particle as a list of points
                // FIXME using the wand at the centroid fails on horseshoes.
                wand.autoOutline((int) xCentroids[particleIndex], (int) yCentroids[particleIndex]);
                nPoints = wand.npoints;
                xPoints = wand.xpoints;
                yPoints = wand.ypoints;

                // Convert the list of points into a polygonal ROI
                PolygonRoi polygonalParticle = new PolygonRoi(xPoints, yPoints, nPoints, Roi.POINT);
                
                // The important part: create a convex hull from the polygon
                Polygon hull = polygonalParticle.getConvexHull();

                // Add the new hull to the end of he list for the current frame
                hulls[iFrame - 1].add(hull);

                Rectangle bounds = hull.getBounds();
                IJ.log("Added hull in frame " + iFrame + " at ( " + bounds.getX() + ", " + bounds.getY() + ") @ " + bounds.getWidth() + " x " + bounds.getHeight() + ".");

            } // particle loop

            IJ.showProgress((double)iFrame/nFrames);

        } // frame loop

        // Start a new stack by duplicating the old one, to keep properties.
        ImagePlus hullStack = new Duplicator().run(imp);

        Filler fil = new Filler();
        // Clear the new stack
        for (int i = 1; i <= nFrames; i++) {
            hullStack.setSlice(i); // 1-based
            fil.clear(hullStack.getProcessor());
        }

        // Rename
        hullStack.setTitle("Particle Convex Hulls");

        // Draw the hulls, frame by frame
        for (int currentFrame = 1; currentFrame < nFrames; currentFrame++) {
            List frameHulls = hulls[currentFrame];
            hullStack.setSlice(currentFrame);
            for (int currentHullIndex = 0; currentHullIndex < frameHulls.size(); currentHullIndex++) {
                hullStack.getProcessor().drawPolygon( (Polygon) frameHulls.get(currentHullIndex) );
            }
        }

        // Move back to beginning
        hullStack.setSlice(1);

        // Make the new stack visible
        hullStack.show();

    }

}
