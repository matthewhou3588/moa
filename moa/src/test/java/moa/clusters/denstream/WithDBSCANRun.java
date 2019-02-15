package moa.clusters.denstream;

import com.yahoo.labs.samoa.instances.DenseInstance;
import com.yahoo.labs.samoa.instances.Instance;
import moa.cluster.Clustering;
import moa.clusterers.AbstractClusterer;
import moa.clusterers.ClusterGenerator;
import moa.clusterers.Clusterer;
import moa.core.AutoClassDiscovery;
import moa.core.AutoExpandVector;
import moa.evaluation.MeasureCollection;
import moa.gui.clustertab.ClusteringAlgoPanel;
import moa.gui.visualization.DataPoint;
import moa.options.ClassOption;
import moa.streams.clustering.ClusterEvent;
import moa.streams.clustering.ClusterEventListener;
import moa.streams.clustering.ClusteringStream;
import moa.streams.clustering.RandomRBFGeneratorEvents;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WithDBSCANRun implements Runnable, ActionListener, ClusterEventListener {

    private WithDBSCANTest testDBSCANCase;

    /* the clusterer */
    private AbstractClusterer m_clusterer0;
    private AbstractClusterer m_clusterer1;

    /* all possible clusterings */
    //not pretty to have all the clusterings, but otherwise we can't just redraw clusterings
    private Clustering gtClustering0 = null;
    private Clustering macro0 = null;
    private Clustering micro0 = null;

    /* pause cluster after point nums */
    private int pauseInterval;


    /* the stream that delivers the instances */
    private ClusteringStream m_stream0;

    /** factor to control the speed */
    private int m_wait_frequency = 1000;

    /* amount of relevant instances; older instances will be dropped;
       creates the 'sliding window' over the stream;
       is strongly connected to the decay rate and decay threshold*/
    private int m_stream0_decayHorizon;

    /* the decay threshold defines the minimum weight of an instance to be relevant */
    private double m_stream0_decay_threshold;

    /* the decay rate of the stream, often reffered to as lambda;
       is being calculated from the horizion and the threshold
       as these are more intuitive to define */
    private double m_stream0_decay_rate;

    /* amount of instances to process in one step*/
    private int m_processFrequency;

    /* flags to control the run behavior */
    private static boolean work;
    private boolean stop = false;

    /* total amount of processed instances */
    private static int timestamp;
    private static int lastPauseTimestamp;


    private Class<?>[] measure_classes;

    /* the measure collections contain all the measures */
    private MeasureCollection[] m_measures0 = null;

    /* holds all the events that have happend, if the stream supports events */
    private ArrayList<ClusterEvent> clusterEvents;

    private ClassOption streamOption = new ClassOption("Stream", 's',
            "", ClusteringStream.class,
            "RandomRBFGeneratorEvents");

    private ClassOption algorithmOption0 = new ClassOption("Algorithm0", 'a',
            "Algorithm to use.", Clusterer.class, "moa.clusterers.denstream.WithDBSCAN");


    public WithDBSCANRun(){
        try {
//            m_stream0 = (ClusteringStream) ClassOption.cliStringToObject(streamOption.getValueAsCLIString(), ClusteringStream.class, null);

            testDBSCANCase = new WithDBSCANTest();
            pauseInterval = 5000;
            m_stream0 = getStream();

            m_stream0_decayHorizon = m_stream0.getDecayHorizon();
            m_stream0_decay_threshold = m_stream0.getDecayThreshold();
            m_stream0_decay_rate = (Math.log(1.0 / m_stream0_decay_threshold) / Math.log(2) / m_stream0_decayHorizon);

            timestamp = 0;
            lastPauseTimestamp = 0;
            work = true;


            if(m_stream0 instanceof RandomRBFGeneratorEvents){
                ((RandomRBFGeneratorEvents)m_stream0).addClusterChangeListener(this);
                clusterEvents = new ArrayList<ClusterEvent>();
//                m_graphcanvas.setClusterEventsList(clusterEvents);
            }
            m_stream0.prepareForUse();

            m_clusterer0 = getClusterer0();
            m_clusterer0.prepareForUse();

            m_clusterer1 = null;

            measure_classes = findMeasureClasses();
            m_measures0 = getMeasures();


            /* TODO this option needs to move from the stream panel to the setup panel */
            m_processFrequency = m_stream0.getEvaluationFrequency();

            //get those values from the generator
//            int dims = m_stream0.numAttsOption.getValue();
//            visualPanel.setDimensionComobBoxes(dims);
//            visualPanel.setPauseInterval(initialPauseInterval);
//
//            m_evalPanel.setMeasures(m_measures0, m_measures1, this);
//            m_graphcanvas.setGraph(m_measures0[0], m_measures1[0],0,m_processFrequency);

        }
        catch(Exception ex) {
            System.out.println("Problem creating instance of stream");
        }
    }

    private Class<?>[] findMeasureClasses() {
        AutoExpandVector<Class<?>> finalClasses = new AutoExpandVector<Class<?>>();
        Class<?>[] classesFound = AutoClassDiscovery.findClassesOfType("moa.evaluation",
                MeasureCollection.class);
        for (Class<?> foundClass : classesFound) {
            //Not add ClassificationMeasureCollection
            boolean noClassificationMeasures = true;
            for (Class cl: foundClass.getInterfaces()) {
                if (cl.toString().contains("moa.evaluation.ClassificationMeasureCollection")){
                    noClassificationMeasures = false;
                }
            }
            if (noClassificationMeasures ) {
                finalClasses.add(foundClass);
            }
        }
        return finalClasses.toArray(new Class<?>[finalClasses.size()]);
    }


    private MeasureCollection[]  getMeasures() {
        ArrayList<MeasureCollection> measuresSelect = new ArrayList<MeasureCollection>();

        int counter = 0;
        for (int i = 0; i < measure_classes.length; i++) {
            try {
                MeasureCollection m = (MeasureCollection) measure_classes[i].newInstance();
                boolean addMeasure = false;
                for (int j = 0; j < m.getNumMeasures(); j++) {
//                    boolean selected = checkboxes.get(counter).isSelected();
                    boolean selected = true;
                    m.setEnabled(j, selected);
                    if (selected) {
                        addMeasure = true;
                    }
                    counter++;
                }
                if (addMeasure) {
                    measuresSelect.add(m);
                }



            } catch (Exception ex) {
                Logger.getLogger("Couldn't create Instance for " + measure_classes[i].getName());
                ex.printStackTrace();
            }
        }


        MeasureCollection[] measures = new MeasureCollection[measuresSelect.size()];
        for (int i = 0; i < measures.length; i++) {
            measures[i] = measuresSelect.get(i);
        }
        return measures;
    }





    public AbstractClusterer getClusterer0(){
        AbstractClusterer c = null;
//        applyChanges();
        try {
            c = (AbstractClusterer) ClassOption.cliStringToObject(algorithmOption0.getValueAsCLIString(), Clusterer.class, null);
        } catch (Exception ex) {
            Logger.getLogger(ClusteringAlgoPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
        return c;
    }

    public ClusteringStream getStream(){
        ClusteringStream s = null;
//        applyChanges();
        try {
            s = (ClusteringStream) ClassOption.cliStringToObject(streamOption.getValueAsCLIString(), ClusteringStream.class, null);
        } catch (Exception ex) {
            Logger.getLogger(ClusteringAlgoPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
        return s;
    }

//    @Override
//    public String toString() {
//        return "WithDBSCANRun{" +
//                "m_stream0=" + ClassOption.objectToCLIString(m_stream0, RandomRBFGeneratorEvents.class) +
//                ", m_stream0_decayHorizon=" + m_stream0_decayHorizon +
//                ", m_stream0_decay_threshold=" + m_stream0_decay_threshold +
//                ", m_stream0_decay_rate=" + m_stream0_decay_rate +
//                '}';
//    }


    @Override
    public String toString() {
        return "WithDBSCANRun{" +
                "m_stream0=" + ClassOption.objectToCLIString(m_stream0, RandomRBFGeneratorEvents.class) +
                "m_wait_frequency=" + m_wait_frequency +
                ", m_stream0_decayHorizon=" + m_stream0_decayHorizon +
                ", m_stream0_decay_threshold=" + m_stream0_decay_threshold +
                ", m_stream0_decay_rate=" + m_stream0_decay_rate +
                ", m_processFrequency=" + m_processFrequency +
                ", stop=" + stop +
                ", streamOption=" + streamOption.getValueAsCLIString() +
                ", algorithmOption0=" + algorithmOption0.getValueAsCLIString() +
                '}';

    }

    public void runCluster() {
        int processCounter = 0;
        int speedCounter = 0;
        LinkedList<DataPoint> pointBuffer0 = new LinkedList<DataPoint>();
        LinkedList<DataPoint> pointBuffer1 = new LinkedList<DataPoint>();
        ArrayList<DataPoint> pointarray0 = null;
        ArrayList<DataPoint> pointarray1 = null;


        while(work || processCounter!=0){
            if (m_stream0.hasMoreInstances()) {
                timestamp++;
                speedCounter++;
                processCounter++;
//                if(timestamp%100 == 0){
//                    m_visualPanel.setProcessedPointsCounter(timestamp);
//                }

                Instance next0 = m_stream0.nextInstance().getData();
                DataPoint point0 = new DataPoint(next0,timestamp);

                pointBuffer0.add(point0);
                while(pointBuffer0.size() > m_stream0_decayHorizon){
                    pointBuffer0.removeFirst();
                }

//                DataPoint point1 = null;
//                if(m_clusterer1!=null){
//                    point1 = new DataPoint(next0,timestamp);
//                    pointBuffer1.add(point1);
//                    while(pointBuffer1.size() > m_stream0_decayHorizon){
//                        pointBuffer1.removeFirst();
//                    }
//                }

//                if(m_visualPanel.isEnabledDrawPoints()){
//                    m_streampanel0.drawPoint(point0);
//                    if(m_clusterer1!=null)
//                        m_streampanel1.drawPoint(point1);
//                    if(processCounter%m_redrawInterval==0){
//                        m_streampanel0.applyDrawDecay(m_stream0_decayHorizon/(float)(m_redrawInterval));
//                        if(m_clusterer1!=null)
//                            m_streampanel1.applyDrawDecay(m_stream0_decayHorizon/(float)(m_redrawInterval));
//                    }
//                }

                Instance traininst0 = new DenseInstance(point0);
                if(m_clusterer0.keepClassLabel())
                    traininst0.setDataset(point0.dataset());
                else
                    traininst0.deleteAttributeAt(point0.classIndex());
                m_clusterer0.trainOnInstanceImpl(traininst0);


//                if(m_clusterer1!=null){
//                    Instance traininst1 = new DenseInstance(point1);
//                    if(m_clusterer1.keepClassLabel())
//                        traininst1.setDataset(point1.dataset());
//                    else
//                        traininst1.deleteAttributeAt(point1.classIndex());
//                    m_clusterer1.trainOnInstanceImpl(traininst1);
//                }

                if (processCounter >= m_processFrequency) {
                    processCounter = 0;
                    for(DataPoint p:pointBuffer0)
                        p.updateWeight(timestamp, m_stream0_decay_rate);

                    pointarray0 = new ArrayList<DataPoint>(pointBuffer0);

//                    if(m_clusterer1!=null){
//                        for(DataPoint p:pointBuffer1)
//                            p.updateWeight(timestamp, m_stream0_decay_rate);
//
//                        pointarray1 = new ArrayList<DataPoint>(pointBuffer1);
//                    }


                    processClusterings(pointarray0);

//                   int pauseInterval = m_visualPanel.getPauseInterval();
                    if(pauseInterval!=0 && lastPauseTimestamp+pauseInterval<=timestamp){
//                        m_visualPanel.toggleVisualizer(true);
                        testDBSCANCase.toggleState();
                    }

                }
            } else {
                System.out.println("DONE");
                return;
            }
            if(speedCounter > m_wait_frequency*30 && m_wait_frequency < 15){
                try {
                    synchronized (this) {
                        if(m_wait_frequency == 0)
                            wait(50);
                        else
                            wait(1);
                    }
                } catch (InterruptedException ex) {

                }
                speedCounter = 0;
            }
        }
        if(!stop){
//            m_streampanel0.drawPointPanels(pointarray0, timestamp, m_stream0_decay_rate, m_stream0_decay_threshold);
//            if(m_clusterer1!=null)
//                m_streampanel1.drawPointPanels(pointarray1, timestamp, m_stream0_decay_rate, m_stream0_decay_threshold);
            work_pause();
        }
    }


    public static void pause(){
        work = false;
        lastPauseTimestamp = timestamp;
    }

    public static void resume(){
        work = true;
    }

    public void stop(){
        work = false;
        stop = true;
    }

    private void processClusterings(ArrayList<DataPoint> points0){
        gtClustering0 = new Clustering(points0);
//        gtClustering1 = null;
//        if(m_clusterer1!=null){
//            gtClustering1 = new Clustering(points1);
//        }

        Clustering evalClustering0 = null;
        Clustering evalClustering1 = null;

        //special case for ClusterGenerator
        if(gtClustering0!= null){
            if(m_clusterer0 instanceof ClusterGenerator)
                ((ClusterGenerator)m_clusterer0).setSourceClustering(gtClustering0);
//            if(m_clusterer1 instanceof ClusterGenerator)
//                ((ClusterGenerator)m_clusterer1).setSourceClustering(gtClustering1);
        }

        macro0 = m_clusterer0.getClusteringResult();
        evalClustering0 = macro0;


        //TODO: should we check if micro/macro is being drawn or needed for evaluation and skip otherwise to speed things up?
        if(m_clusterer0.implementsMicroClusterer()){
            micro0 = m_clusterer0.getMicroClusteringResult();
            if(macro0 == null && micro0 != null){
                //TODO: we need a Macro Clusterer Interface and the option for kmeans to use the non optimal centers
                macro0 = moa.clusterers.KMeans.gaussianMeans(gtClustering0, micro0);
            }
            if(m_clusterer0.evaluateMicroClusteringOption.isSet())
                evalClustering0 = micro0;
            else
                evalClustering0 = macro0;
        }

//        if(m_clusterer1!=null){
//            macro1 = m_clusterer1.getClusteringResult();
//            evalClustering1 = macro1;
//            if(m_clusterer1.implementsMicroClusterer()){
//                micro1 = m_clusterer1.getMicroClusteringResult();
//                if(macro1 == null && micro1 != null){
//                    macro1 = moa.clusterers.KMeans.gaussianMeans(gtClustering1, micro1);
//                }
//                if(m_clusterer1.evaluateMicroClusteringOption.isSet())
//                    evalClustering1 = micro1;
//                else
//                    evalClustering1 = macro1;
//            }
//        }

        evaluateClustering(evalClustering0, gtClustering0, points0, true);
//        evaluateClustering(evalClustering1, gtClustering1, points1, false);

//        drawClusterings(points0, points1);
    }

    public void showClusterMetrics(){
        DecimalFormat d = new DecimalFormat("0.00");
        StringBuilder sb = new StringBuilder();

            int counter = 0;
            for (MeasureCollection m : m_measures0) {
                for (int i = 0; i < m.getNumMeasures(); i++) {
                    if(!m.isEnabled(i)) continue;
                    if(Double.isNaN(m.getLastValue(i)))
//                        values.get(counter*4).setText("-");
                        sb.append(m.getName(i)).append("\t").append("-").append("\n");
                    else {
//                        values.get(counter*4).setText(d.format(m.getLastValue(i)));
                        sb.append(m.getName(i)).append("\tcurrent\t").append(d.format(m.getLastValue(i))).append("\n");
                    }

                    if(Double.isNaN(m.getMean(i)))
//                        values.get(counter*4+2).setText("-");
                        sb.append(m.getName(i)).append("\t").append("-").append("\n");

                    else {
//                        values.get(counter*4+2).setText(d.format(m.getMean(i)));
                        sb.append(m.getName(i)).append("\tmean\t").append(d.format(m.getMean(i))).append("\n");
                    }
                    counter++;
                }
            }


        System.out.println(sb.toString());
    }

    private void evaluateClustering(Clustering found_clustering, Clustering trueClustering, ArrayList<DataPoint> points, boolean algorithm0){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < m_measures0.length; i++) {
                if(found_clustering!=null && found_clustering.size() > 0){
                    try {
                        double msec = m_measures0[i].evaluateClusteringPerformance(found_clustering, trueClustering, points);
                        sb.append(m_measures0[i].getClass().getSimpleName()+" took "+msec+"ms (Mean:"+m_measures0[i].getMeanRunningTime()+")");
                        sb.append("\n");

                    } catch (Exception ex) { ex.printStackTrace(); }
                }
                else{
                    for(int j = 0; j < m_measures0[i].getNumMeasures(); j++){
                        m_measures0[i].addEmptyValue(j);
                    }
                }
        }

//        System.out.println(sb.toString());
    }

    private void work_pause(){
        while(!work && !stop){
            try {
                synchronized (this) {
                    wait(1000);
                }
            } catch (InterruptedException ex) {

            }
        }
        run();
    }

    @Override
    public void changeCluster(ClusterEvent e) {
        if(clusterEvents!=null) clusterEvents.add(e);
        System.out.println(e.getType()+": "+e.getMessage());
    }

    @Override
    public void actionPerformed(ActionEvent e) {

    }

    @Override
    public void run() { runCluster(); }

    public static void main(String[] args) {
        WithDBSCANRun obj = new WithDBSCANRun();
        System.out.println(obj);


//        obj.run();
    }
}
