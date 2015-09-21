package pl.net.lkd.vectorizer.gui

import ch.akuhn.edu.mit.tedlab.DMat
import pitt.search.semanticvectors.ObjectVector
import pitt.search.semanticvectors.vectors.RealVector
import pitt.search.semanticvectors.viz.Plot2dVectors
import pitt.search.semanticvectors.viz.PrincipalComponents

import javax.swing.JFrame
import java.awt.Color
import java.awt.Dimension

/**
 * Created by lukasz on 13/10/14.
 */
class VectorPlotter extends Plot2dVectors {

    public VectorPlotter(ObjectVector[] vectors) {
        super(vectors)
//        this.background = Color.WHITE
    }

    public static void plot(PrincipalComponents pcs) {
        DMat reducedVectors = pcs.svdR.Ut
        ObjectVector[] vectorsToPlot = new ObjectVector[pcs.vectorInput.length]
        int truncate = 4
        for (int i = 0; i < pcs.vectorInput.length; i++) {
            float[] tempVec = new float[truncate];
            for (int j = 0; j < truncate; ++j) {
                tempVec[j] = (float) (reducedVectors.value[j][i])
            }
            vectorsToPlot[i] = new ObjectVector(pcs.vectorInput[i].getObject().toString(), new RealVector(tempVec))
        }
        VectorPlotter plot = new VectorPlotter(vectorsToPlot)
        JFrame frame = new JFrame("Term Vector Plotter");
        frame.setSize(new Dimension(Plot2dVectors.scale + 2 * Plot2dVectors.pad, Plot2dVectors.scale + 2 * Plot2dVectors.pad));
        frame.setBackground(Color.WHITE)
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(plot);
        frame.setVisible(true);
    }
}
