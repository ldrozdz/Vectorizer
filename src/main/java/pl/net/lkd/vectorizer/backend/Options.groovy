package pl.net.lkd.vectorizer.backend

/**
 * Created by lukasz on 13/10/14.
 */
class Options {
    String docpath
    String luceneindexpath
    Boolean positionalindex = true

    public String[] getSVCommandLine() {
        String[] cl = ['luceneindexpath'].collectMany {
            if (this[it]) {
                return ["-${it}", this[it]]
            }
        }
        if (positionalindex) {
            cl += ['-windowradius', "10"]
            cl += ['-queryvectorfile', "${luceneindexpath}/termtermvectors.bin"]
        } else {
            cl += ['-queryvectorfile', "${luceneindexpath}/termvectors.bin"]
        }
        cl += ['-termvectorsfile', "${luceneindexpath}/termvectors.bin"]
        cl += ['-termtermvectorsfile', "${luceneindexpath}/termtermvectors.bin"]
        cl += ['-docvectorsfile', "${luceneindexpath}/docvectors.bin"]
        return cl
    }

    public String[] getSVCommandLine(String[] query) {
        return getSVCommandLine() + query
    }

}
