/** Copyright (c) 2012 Memorial Sloan-Kettering Cancer Center.
**
** This library is free software; you can redistribute it and/or modify it
** under the terms of the GNU Lesser General Public License as published
** by the Free Software Foundation; either version 2.1 of the License, or
** any later version.
**
** This library is distributed in the hope that it will be useful, but
** WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
** MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
** documentation provided hereunder is on an "as is" basis, and
** Memorial Sloan-Kettering Cancer Center 
** has no obligations to provide maintenance, support,
** updates, enhancements or modifications.  In no event shall
** Memorial Sloan-Kettering Cancer Center
** be liable to any party for direct, indirect, special,
** incidental or consequential damages, including lost profits, arising
** out of the use of this software and its documentation, even if
** Memorial Sloan-Kettering Cancer Center 
** has been advised of the possibility of such damage.  See
** the GNU Lesser General Public License for more details.
**
** You should have received a copy of the GNU Lesser General Public License
** along with this library; if not, write to the Free Software Foundation,
** Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
**/

package org.mskcc.cbio.portal.scripts;

import java.io.*;
import java.util.*;
import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;
import org.mskcc.cbio.portal.dao.*;
import org.mskcc.cbio.portal.model.*;
import org.mskcc.cbio.portal.util.*;

/**
 * Code to Import Copy Number Alteration or MRNA Expression Data.
 *
 * @author Ethan Cerami
 */
public class ImportTabDelimData {
    private HashSet<Long> importedGeneSet = new HashSet<Long>();
    private HashSet<String> importedMicroRNASet = new HashSet<String>();
    private static Logger logger = Logger.getLogger(ImportTabDelimData.class);

    /**
     * Barry Target Line:  A constant currently used to indicate the RAE method.
     */
    public static final String BARRY_TARGET = "Barry";

    /**
     * Consensus Target Line:  A constant currently used to indicate consensus of multiple
     * CNA calling algorithms.
     */
    public static final String CONSENSUS_TARGET = "consensus";

    private ProgressMonitor pMonitor;
    private File mutationFile;
    private String targetLine;
    private int geneticProfileId;
    private GeneticProfile geneticProfile;
    private HashSet<String> microRnaIdSet;

    /**
     * Constructor.
     *
     * @param dataFile         Data File containing CNA data.
     * @param targetLine       The line we want to import.
     *                         If null, all lines are imported.
     * @param geneticProfileId GeneticProfile ID.
     * @param pMonitor         Progress Monitor Object.
     */
    public ImportTabDelimData(File dataFile, String targetLine, int geneticProfileId,
            ProgressMonitor pMonitor) {
        this.mutationFile = dataFile;
        this.targetLine = targetLine;
        this.geneticProfileId = geneticProfileId;
        this.pMonitor = pMonitor;
    }

    /**
     * Constructor.
     *
     * @param dataFile         Data File containing CNA data.
     * @param geneticProfileId GeneticProfile ID.
     * @param pMonitor         Progress Monitor Object.
     */
    public ImportTabDelimData(File dataFile, int geneticProfileId, ProgressMonitor pMonitor) {
        this.mutationFile = dataFile;
        this.geneticProfileId = geneticProfileId;
        this.pMonitor = pMonitor;
    }

    /**
     * Import the CNA Data.
     *
     * @throws IOException  IO Error.
     * @throws DaoException Database Error.
     */
    public void importData() throws IOException, DaoException {
        DaoMicroRna daoMicroRna = new DaoMicroRna();
        microRnaIdSet = daoMicroRna.getEntireSet();

        geneticProfile = DaoGeneticProfile.getGeneticProfileById(geneticProfileId);

        FileReader reader = new FileReader(mutationFile);
        BufferedReader buf = new BufferedReader(reader);
        String headerLine = buf.readLine();
        String parts[] = headerLine.split("\t");

        int caseStartIndex = getStartIndex(parts);
        int hugoSymbolIndex = getHugoSymbolIndex(parts);
        int entrezGeneIdIndex = getEntrezGeneIdIndex(parts);
        
        String caseIds[];

        //  Branch, depending on targetLine setting
        if (targetLine == null) {
            caseIds = new String[parts.length - caseStartIndex];
            System.arraycopy(parts, caseStartIndex, caseIds, 0, parts.length - caseStartIndex);
        } else {
            caseIds = new String[parts.length - caseStartIndex];
            System.arraycopy(parts, caseStartIndex, caseIds, 0, parts.length - caseStartIndex);
        }
		convertBarcodes(caseIds);
        pMonitor.setCurrentMessage("Import tab delimited data for " + caseIds.length + " cases.");

        // Add Cases to the Database
        ArrayList <String> orderedCaseList = new ArrayList<String>();
        for (int i = 0; i < caseIds.length; i++) {
            if (!DaoCaseProfile.caseExistsInGeneticProfile(caseIds[i],
                    geneticProfileId)) {
                DaoCaseProfile.addCaseProfile(caseIds[i], geneticProfileId);
            }
            orderedCaseList.add(caseIds[i]);
        }
        DaoGeneticProfileCases daoGeneticProfileCases = new DaoGeneticProfileCases();
        daoGeneticProfileCases.addGeneticProfileCases(geneticProfileId, orderedCaseList);

        String line = buf.readLine();
        int numRecordsStored = 0;

        DaoGeneOptimized daoGene = DaoGeneOptimized.getInstance();

        DaoGeneticAlteration daoGeneticAlteration = DaoGeneticAlteration.getInstance();
        DaoMicroRnaAlteration daoMicroRnaAlteration = DaoMicroRnaAlteration.getInstance();

        int lenParts = parts.length;
        
        while (line != null) {
            if (pMonitor != null) {
                pMonitor.incrementCurValue();
                ConsoleUtil.showProgress(pMonitor);
            }
            
            //  Ignore lines starting with #
            if (!line.startsWith("#") && line.trim().length() > 0) {
                parts = line.split("\t",-1);
                
                if (parts.length>lenParts) {
                    System.err.println("The following line has more fields (" + parts.length
                            + ") than the headers(" + lenParts + "): \n"+parts[0]);
                }
                String values[] = (String[]) ArrayUtils.subarray(parts, caseStartIndex, parts.length>lenParts?lenParts:parts.length);

                String hugo = parts[hugoSymbolIndex];
                if (hugo!=null && hugo.isEmpty()) {
                    hugo = null;
                }
                
                String entrez = null;
                if (entrezGeneIdIndex!=-1) {
                    entrez = parts[entrezGeneIdIndex];
                }
                if (entrez!=null && !entrez.matches("-?[0-9]+")) {
                    entrez = null;
                }

                if (hugo != null || entrez != null) {
                    if (hugo != null && (hugo.contains("///") || hugo.contains("---"))) {
                        //  Ignore gene IDs separated by ///.  This indicates that
                        //  the line contains information regarding multiple genes, and
                        //  we cannot currently handle this.
                        //  Also, ignore gene IDs that are specified as ---.  This indicates
                        //  the line contains information regarding an unknown gene, and
                        //  we cannot currently handle this.
                        logger.debug("Ignoring gene ID:  " + hugo);
                    } else {
                        List<CanonicalGene> genes = null;
                        if (entrez!=null) {
                            CanonicalGene gene = daoGene.getGene(Long.parseLong(entrez));
                            if (gene!=null) {
                                genes = Arrays.asList(gene);
                            }
                        } 
                        
                        if (genes==null && hugo != null) {
                            // deal with multiple symbols separate by |, use the first one
                            int ix = hugo.indexOf("|");
                            if (ix>0) {
                                hugo = hugo.substring(0, ix);
                            }

                            genes = daoGene.guessGene(hugo);
                        }

                        if (genes == null) {
                            genes = Collections.emptyList();
                        }

                        //  If no target line is specified or we match the target, process.
                        if (targetLine == null || parts[0].equals(targetLine)) {
                            if (genes.isEmpty()) {
                                //  if gene is null, we might be dealing with a micro RNA ID
                                if (hugo != null && hugo.toLowerCase().contains("-mir-")) {
//                                    if (microRnaIdSet.contains(geneId)) {
//                                        storeMicroRnaAlterations(values, daoMicroRnaAlteration, geneId);
//                                        numRecordsStored++;
//                                    } else {
                                        pMonitor.logWarning("microRNA is not known to me:  [" + hugo
                                            + "]. Ignoring it "
                                            + "and all tab-delimited data associated with it!");
//                                    }
                                } else {
                                    String gene = (hugo != null) ? hugo : entrez;
                                    pMonitor.logWarning("Gene not found:  [" + gene
                                        + "]. Ignoring it "
                                        + "and all tab-delimited data associated with it!");
                                }
                            } else if (genes.size()==1) {
                                storeGeneticAlterations(values, daoGeneticAlteration, genes.get(0));
                                if (geneticProfile!=null
                                        && geneticProfile.getGeneticAlterationType() == GeneticAlterationType.COPY_NUMBER_ALTERATION
                                        && geneticProfile.showProfileInAnalysisTab()) {
                                    storeCna(genes.get(0).getEntrezGeneId(), orderedCaseList, values);
                                }
                                
                                numRecordsStored++;
                            } else {
                                for (CanonicalGene gene : genes) {
                                    if (gene.isMicroRNA()) { // for micro rna, duplicate the data
                                        storeGeneticAlterations(values, daoGeneticAlteration, gene);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            line = buf.readLine();
        }
        if (MySQLbulkLoader.isBulkLoad()) {
           MySQLbulkLoader.flushAll();
        }
        
        if (numRecordsStored == 0) {
            throw new DaoException ("Something has gone wrong!  I did not save any records" +
                    " to the database!");
        }
    }

    private void storeMicroRnaAlterations(String[] values,
            DaoMicroRnaAlteration daoMicroRnaAlteration, String microRnaId) throws DaoException {

        //  Check that we have not already imported information regarding this microRNA.
        //  This is an important check, because a GISTIC or RAE file may contain
        //  multiple rows for the same gene, and we only want to import the first row.
        if (!importedMicroRNASet.contains(microRnaId)) {
            daoMicroRnaAlteration.addMicroRnaAlterations(geneticProfileId, microRnaId, values);
            importedMicroRNASet.add(microRnaId);
        }
    }

    private void storeGeneticAlterations(String[] values, DaoGeneticAlteration daoGeneticAlteration,
            CanonicalGene gene) throws DaoException {

        //  Check that we have not already imported information regarding this gene.
        //  This is an important check, because a GISTIC or RAE file may contain
        //  multiple rows for the same gene, and we only want to import the first row.
        if (!importedGeneSet.contains(gene.getEntrezGeneId())) {
            daoGeneticAlteration.addGeneticAlterations(geneticProfileId, gene.getEntrezGeneId(), values);
            importedGeneSet.add(gene.getEntrezGeneId());
        }
    }
    
    private void storeCna(long entrezGeneId, ArrayList <String> cases, String[] values) throws DaoException {
        int n = values.length;
        if (n==0)
            System.out.println();
        int i = values[0].equals(""+entrezGeneId) ? 1:0;
        for (; i<n; i++) {
            if (values[i].equals(GeneticAlterationType.AMPLIFICATION) 
                    || values[i].equals(GeneticAlterationType.HOMOZYGOUS_DELETION)) {
                CnaEvent event = new CnaEvent(cases.get(i), geneticProfileId, entrezGeneId, Short.parseShort(values[i]));
                DaoCnaEvent.addCaseCnaEvent(event);
            }
        }
    }
    
    private int getHugoSymbolIndex(String[] headers) {
        return targetLine==null ? 0 : 1;
    }
    
    private int getEntrezGeneIdIndex(String[] headers) {
        for (int i = 0; i<headers.length; i++) {
            if (headers[i].equalsIgnoreCase("Entrez_Gene_Id")) {
                return i;
            }
        }
        return -1;
    }

    private int getStartIndex(String[] headers) {
        int startIndex = targetLine==null ? 1 : 2;
        
        for (int i=startIndex; i<headers.length; i++) {
            String h = headers[i];
            if (!h.equalsIgnoreCase("Gene Symbol") &&
                    !h.equalsIgnoreCase("Hugo_Symbol") &&
                    !h.equalsIgnoreCase("Entrez_Gene_Id") &&
                    !h.equalsIgnoreCase("Locus ID") &&
                    !h.equalsIgnoreCase("Cytoband")) {
                return i;
            }
        }
        
        return startIndex;
    }

	private void convertBarcodes(String caseIds[])
	{
		for (int lc = 0; lc < caseIds.length; lc++) {
			caseIds[lc] = CaseIdUtil.getCaseId(caseIds[lc]);
		}
	}
}