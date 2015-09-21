package pl.net.lkd.vectorizer.backend

import ch.akuhn.edu.mit.tedlab.DMat
import groovy.util.logging.Slf4j
import pitt.search.semanticvectors.FlagConfig
import pitt.search.semanticvectors.ObjectVector
import pitt.search.semanticvectors.Search
import pitt.search.semanticvectors.SearchResult
import pitt.search.semanticvectors.vectors.RealVector
import pitt.search.semanticvectors.viz.Plot2dVectors
import pitt.search.semanticvectors.viz.PrincipalComponents
import pl.net.lkd.vectorizer.gui.VectorPlotter

import javax.swing.*
import java.awt.*
import java.util.List

@Slf4j
class Searcher {
    Options opts
    FlagConfig config

    public Searcher(Options opts) {
        this.opts = opts
    }

    public List<SearchResult> search(String[] queryTerms) throws IOException {
        this.config = FlagConfig.getFlagConfig(opts.getSVCommandLine(queryTerms))
        LinkedList<SearchResult> results = Search.runSearch(this.config)
        return results
    }

    public void plotVectors(List<SearchResult> results) {
        ObjectVector[] resultsVectors = Search.getSearchResultVectors(this.config)
        PrincipalComponents pcs = new PrincipalComponents(resultsVectors)
        VectorPlotter.plot(pcs)
    }

}
