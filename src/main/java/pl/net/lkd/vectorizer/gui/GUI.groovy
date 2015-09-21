package pl.net.lkd.vectorizer.gui

import groovy.swing.SwingBuilder
import pitt.search.semanticvectors.SearchResult
import pl.net.lkd.vectorizer.backend.Indexer
import pl.net.lkd.vectorizer.backend.Options
import pl.net.lkd.vectorizer.backend.Searcher

import javax.swing.*
import java.awt.*
import java.util.List

class GUI {
    SwingBuilder swing = new SwingBuilder()
    Options options
    JFrame mainFrame
    JPanel activePanel

    public static void main(String[] args) {
        def gui = new GUI()
        gui.buildGUI()
    }

    private void buildGUI() {
        def mainMenu =
              swing.menuBar {
                  menu(text: "File", mnemonic: 'F') {
                      menuItem(text: "New", mnemonic: "N", actionPerformed: { showStartPanel() })
                      menuItem(text: "Exit", mnemonic: 'X', actionPerformed: { dispose() })
                  }
              }

        swing.edt {
            mainFrame = swing.frame(title: "Vectorizer",
                                    defaultCloseOperation: JFrame.EXIT_ON_CLOSE,
                                    show: false,
                                    id: "mainFrame") {
                borderLayout()
                widget(mainMenu)
            }
            showStartPanel()
        }

    }

    def showStartPanel() {
        JPanel startPanel
        this.options = new Options()
        boolean createIdx = false

        JFileChooser docFC = swing.fileChooser(
              dialogTitle: "Choose documents to index",
              fileSelectionMode: JFileChooser.DIRECTORIES_ONLY,
              ) {}
        JFileChooser idxFC = swing.fileChooser(
              dialogTitle: "Choose index location",
              fileSelectionMode: JFileChooser.DIRECTORIES_ONLY
        )


        JButton processBtn = swing.button(text: "Process",
                                          enabled: false,
                                          actionPerformed: {
                                              this.options.luceneindexpath = idxFC.selectedFile.path
                                              if (createIdx) {
                                                  this.options.docpath = docFC.selectedFile.path
                                                  Indexer.index(this.options)
                                              }
                                              showQueryPanel()
                                          })

        startPanel = swing.panel(constraints: BorderLayout.WEST, id: 'openPanel') {
            vbox() {
                hbox {
                    label(text: 'New project')
                    button(text: 'Choose documents to index', actionPerformed: {
                        docFC.showOpenDialog()
                        processBtn.enabled = (docFC.selectedFile != null && idxFC.selectedFile != null)
                        createIdx = true
                    })
                    button(text: 'Choose index location', actionPerformed: {
                        idxFC.showOpenDialog()
                        processBtn.enabled = (docFC.selectedFile != null && idxFC.selectedFile != null)
                    })
                }
                separator()
                hbox {
                    label(text: 'Existing project')
                    button(text: 'Choose index location', actionPerformed: {
                        idxFC.showOpenDialog()
                        processBtn.enabled = (idxFC.selectedFile != null)
                        createIdx = false
                    })
                }
                separator()
                widget(processBtn)
            }
        }

        if (activePanel) { mainFrame.remove(activePanel) }
        mainFrame.add(startPanel)
        activePanel = startPanel
        mainFrame.pack()
        mainFrame.setVisible(true)
    }

    def showQueryPanel() {
        Searcher searcher = new Searcher(this.options)
        JPanel queryPanel
        JTextField queryFld = swing.textField(columns: 64)
        JProgressBar progress = swing.progressBar(minimum: 0, maximum: 100, value: 0, stringPainted: true)
        JList resultLst = swing.list(cellRenderer: new StripeRenderer())
        JButton searchBtn = swing.button(text: 'Search', enabled: true, actionPerformed: {
            swing.doOutside {
                List<SearchResult> results = searcher.search(queryFld.text.split())
                searcher.plotVectors(results)
                doLater {
                    resultLst.listData = results.collect { SearchResult r -> "${r.score} ${r.objectVector.object}" }
                    mainFrame.pack()
                }
            }
        })

        queryPanel = swing.panel {
            vbox {
                hbox {
                    label(text: 'Query:')
                    widget(queryFld)
                    widget(searchBtn)
                }
                separator()
                hbox {
                    label(text: 'Results:')
                    widget(resultLst)
                }
            }
        }

        if (activePanel) { mainFrame.remove(activePanel) }
        mainFrame.add(queryPanel)
        activePanel = queryPanel
        mainFrame.pack()
        mainFrame.setVisible(true)
    }


}
