package pl.net.lkd.vectorizer.backend

import groovy.io.FileType
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.FieldInfo
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import pitt.search.lucene.FilePositionDoc
import pitt.search.semanticvectors.*
import pitt.search.semanticvectors.utils.VerbatimLogger

class Indexer {

    public static void index(Options opts) {
        buildLuceneIndex(opts)
        if (opts.positionalindex) {
            buildSVPositionalIndex(opts)
        } else {
            buildSVIndex(opts)
        }
    }

    private static void buildLuceneIndex(Options opts) {
        boolean create = true;
        File docDir = new File(opts.docpath)
        File indexDir = new File(opts.luceneindexpath)
        if (!docDir.exists() || !docDir.canRead()) {
            System.out.println("Document directory '" + docDir.getAbsolutePath() + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        Date start = new Date();
        try {
            System.out.println("Indexing directory '" + docDir.path + "' to '" + indexDir.path + "'...");

            Directory dir = FSDirectory.open(indexDir);
            Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_46);
            IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_46, analyzer);

            if (create) {
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            } else {
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            }

            IndexWriter writer = new IndexWriter(dir, iwc);
            indexDocs(writer, docDir, opts.positionalindex);

            writer.close();

            Date end = new Date();
            System.out.println(end.getTime() - start.getTime() + " total milliseconds");

        } catch (IOException e) {
            System.out.println(" caught a " + e.getClass() +
                                     "\n with message: " + e.getMessage());
        }
    }

    /**
     * Builds term vector and document vector stores from a Lucene index.
     * @param args [command line options to be parsed] then path to Lucene index
     * @throws IOException  If filesystem resources including Lucene index are unavailable.
     */
    private static void buildSVIndex(Options opts) throws IllegalArgumentException, IOException {
        FlagConfig flagConfig = FlagConfig.getFlagConfig(opts.getSVCommandLine());

        VerbatimLogger.info("Seedlength: " + flagConfig.seedlength()
                                  + ", Dimension: " + flagConfig.dimension()
                                  + ", Vector type: " + flagConfig.vectortype()
                                  + ", Minimum frequency: " + flagConfig.minfrequency()
                                  + ", Maximum frequency: " + flagConfig.maxfrequency()
                                  + ", Number non-alphabet characters: " + flagConfig.maxnonalphabetchars()
                                  + ", Contents fields are: " + Arrays.toString(flagConfig.contentsfields()) + "\n");

        String termFile = "${flagConfig.luceneindexpath()}/${flagConfig.termvectorsfile()}";
        String docFile = "${flagConfig.luceneindexpath()}/${flagConfig.docvectorsfile()}";
        LuceneUtils luceneUtils = new LuceneUtils(flagConfig);

        try {
            TermVectorsFromLucene termVectorIndexer;
            if (!flagConfig.initialtermvectors().isEmpty()) {
                // If Flags.initialtermvectors="random" create elemental (random index)
                // term vectors. Recommended to iterate at least once (i.e. -trainingcycles = 2) to
                // obtain semantic term vectors.
                // Otherwise attempt to load pre-existing semantic term vectors.
                VerbatimLogger.info("Creating elemental term vectors ... \n");
                termVectorIndexer = TermVectorsFromLucene.createTermBasedRRIVectors(flagConfig);
            } else {
                VerbatimLogger.info("Creating term vectors as superpositions of elemental document vectors ... \n");
                termVectorIndexer = TermVectorsFromLucene.createTermVectorsFromLucene(flagConfig, null);
            }

            // Create doc vectors and write vectors to disk.
            switch (flagConfig.docindexing()) {
                case DocVectors.DocIndexingStrategy.INCREMENTAL:
                    VectorStoreWriter.writeVectors(termFile, flagConfig, termVectorIndexer.getSemanticTermVectors());
                    IncrementalDocVectors.createIncrementalDocVectors(termVectorIndexer.getSemanticTermVectors(), flagConfig, luceneUtils);
                    IncrementalTermVectors itermVectors;

                    for (int i = 1; i < flagConfig.trainingcycles(); ++i) {
                        itermVectors = new IncrementalTermVectors(flagConfig, luceneUtils);

                        VectorStoreWriter.writeVectors(
                              VectorStoreUtils.getStoreFileName(flagConfig.termvectorsfile() + flagConfig.trainingcycles(), flagConfig),
                              flagConfig, itermVectors);

                        IncrementalDocVectors.createIncrementalDocVectors(itermVectors, flagConfig, luceneUtils);
                    }
                    break;
                case DocVectors.DocIndexingStrategy.INMEMORY:
                    DocVectors docVectors = new DocVectors(termVectorIndexer.getSemanticTermVectors(), flagConfig, luceneUtils);
                    for (int i = 1; i < flagConfig.trainingcycles(); ++i) {
                        VerbatimLogger.info("\nRetraining with learned document vectors ...");
                        termVectorIndexer = TermVectorsFromLucene.createTermVectorsFromLucene(flagConfig, docVectors);
                        docVectors = new DocVectors(termVectorIndexer.getSemanticTermVectors(), flagConfig, luceneUtils);
                    }
                    // At end of training, convert document vectors from ID keys to pathname keys.
                    VectorStore writeableDocVectors = docVectors.makeWriteableVectorStore();

                    if (flagConfig.trainingcycles() > 1) {
                        termFile = opts.luceneindexpath + "/termvectors" + flagConfig.trainingcycles() + ".bin";
                        docFile = opts.luceneindexpath + "/docvectors" + flagConfig.trainingcycles() + ".bin";
                    }
                    VerbatimLogger.info("Writing term vectors to " + termFile + "\n");
                    VectorStoreWriter.writeVectors(termFile, flagConfig, termVectorIndexer.getSemanticTermVectors());
                    VerbatimLogger.info("Writing doc vectors to " + docFile + "\n");
                    VectorStoreWriter.writeVectors(docFile, flagConfig, writeableDocVectors);
                    break;
                case DocVectors.DocIndexingStrategy.NONE:
                    // Write term vectors to disk even if there are no docvectors to output.
                    VerbatimLogger.info("Writing term vectors to " + termFile + "\n");
                    VectorStoreWriter.writeVectors(termFile, flagConfig, termVectorIndexer.getSemanticTermVectors());
                    break;
                default:
                    throw new IllegalStateException(
                          "No procedure defined for -docindexing " + flagConfig.docindexing());
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void buildSVPositionalIndex(Options opts) {
        VectorStore newElementalTermVectors = null;
        FlagConfig flagConfig = FlagConfig.getFlagConfig(opts.getSVCommandLine())

        if (flagConfig.luceneindexpath().isEmpty()) {
            throw (new IllegalArgumentException("-luceneindexpath must be set."));
        }
        String luceneIndex = flagConfig.luceneindexpath();

        // If initialtermvectors is defined, read these vectors.
        if (!flagConfig.initialtermvectors().isEmpty()) {
            try {
                VectorStoreRAM vsr = new VectorStoreRAM(flagConfig);
                vsr.initFromFile(flagConfig.initialtermvectors());
                newElementalTermVectors = vsr;
                VerbatimLogger.info("Using trained index vectors from vector store " + flagConfig.initialtermvectors());
            } catch (IOException e) {
                throw new IllegalArgumentException();
            }
        }

        String termFile = flagConfig.termtermvectorsfile();

        VerbatimLogger.info("Building positional index, Lucene index: " + luceneIndex
                                  + ", Seedlength: " + flagConfig.seedlength()
                                  + ", Vector length: " + flagConfig.dimension()
                                  + ", Vector type: " + flagConfig.vectortype()
                                  + ", Minimum term frequency: " + flagConfig.minfrequency()
                                  + ", Maximum term frequency: " + flagConfig.maxfrequency()
                                  + ", Number non-alphabet characters: " + flagConfig.maxnonalphabetchars()
                                  + ", Window radius: " + flagConfig.windowradius()
                                  + ", Fields to index: " + Arrays.toString(flagConfig.contentsfields())
                                  + "\n");

        try {
            TermTermVectorsFromLucene termTermIndexer = new TermTermVectorsFromLucene(flagConfig, newElementalTermVectors);

            VectorStoreWriter.writeVectors(termFile, flagConfig, termTermIndexer.getSemanticTermVectors());

            for (int i = 1; i < flagConfig.trainingcycles(); ++i) {
                newElementalTermVectors = termTermIndexer.getSemanticTermVectors();
                VerbatimLogger.info("\nRetraining with learned term vectors ...");
                termTermIndexer = new TermTermVectorsFromLucene(flagConfig, newElementalTermVectors);
            }

            if (flagConfig.trainingcycles() > 1) {
                termFile = termFile.replaceAll("\\..*", "") + flagConfig.trainingcycles() + ".bin";
                VectorStoreWriter.writeVectors(termFile, flagConfig, termTermIndexer.getSemanticTermVectors());
            }

            // Incremental indexing is hardcoded into BuildPositionalIndex.
            // TODO: Understand if this is an appropriate requirement, and whether
            //       the user should be alerted of any potential consequences.
            if (flagConfig.docindexing() != DocVectors.DocIndexingStrategy.NONE) {
                IncrementalDocVectors.createIncrementalDocVectors(
                      termTermIndexer.getSemanticTermVectors(),
                      flagConfig,
                      new LuceneUtils(flagConfig));
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Indexes the given file using the given writer, or if a directory is given,
     * recurses over files and directories found under the given directory.
     *
     * NOTE: This method indexes one document per input file.  This is slow.  For good
     * throughput, put multiple documents into your input file(s).  An example of this is
     * in the benchmark module, which can create "line doc" files, one document per line,
     * using the
     * <a href="../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
     * >WriteLineDocTask</a>.
     *
     * @param writer Writer to the index where the given file/dir info will be stored
     * @param file The file to index, or the directory to recurse into to find files to index
     * @throws IOException If there is a low-level I/O error
     */
    private static void indexDocs(IndexWriter writer, File dir, Boolean positionalIndex) throws IOException {
        final FieldType textField = new FieldType();
        textField.setIndexed(true);
        textField.setIndexOptions(FieldInfo.IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        textField.setStored(true);
        textField.setStoreTermVectors(true);
        textField.setStoreTermVectorPositions(true);
        textField.setTokenized(true);

        // do not try to index files that cannot be read
        dir.eachFileRecurse(FileType.FILES) { file ->
            switch (positionalIndex) {
                case true:
                    Document doc = FilePositionDoc.Document(file);
                    try {
                        System.out.println("adding " + file);
                        writer.addDocument(doc);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case false:
                    FileInputStream fis;
                    try {
                        fis = new FileInputStream(file);
                    } catch (FileNotFoundException fnfe) {
                        return;
                    }

                    try {
                        def txt = fis.text
                        Document doc = new Document();

                        Field pathField = new StringField("path", file.path, Field.Store.YES);
                        doc.add(pathField);
                        doc.add(new LongField("modified", file.lastModified(), Field.Store.NO));
                        if (positionalIndex) {
                            doc.add(new Field("contents", txt, textField));
                        } else {
                            doc.add(new TextField("contents", txt, Field.Store.YES));
                        }
                        if (writer.getConfig().getOpenMode() == IndexWriterConfig.OpenMode.CREATE) {
                            System.out.println("adding " + file);
                            writer.addDocument(doc);
                        } else {
                            System.out.println("updating " + file);
                            writer.updateDocument(new Term("path", file.getPath()), doc);
                        }
                    } finally {
                        fis.close();
                    }
                    break;
            }
        }
    }

}
