<%--
 - Copyright (c) 2015 Memorial Sloan-Kettering Cancer Center.
 -
 - This library is distributed in the hope that it will be useful, but WITHOUT
 - ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF MERCHANTABILITY OR FITNESS
 - FOR A PARTICULAR PURPOSE. The software and documentation provided hereunder
 - is on an "as is" basis, and Memorial Sloan-Kettering Cancer Center has no
 - obligations to provide maintenance, support, updates, enhancements or
 - modifications. In no event shall Memorial Sloan-Kettering Cancer Center be
 - liable to any party for direct, indirect, special, incidental or
 - consequential damages, including lost profits, arising out of the use of this
 - software and its documentation, even if Memorial Sloan-Kettering Cancer
 - Center has been advised of the possibility of such damage.
 --%>

<%--
 - This file is part of cBioPortal.
 -
 - cBioPortal is free software: you can redistribute it and/or modify
 - it under the terms of the GNU Affero General Public License as
 - published by the Free Software Foundation, either version 3 of the
 - License.
 -
 - This program is distributed in the hope that it will be useful,
 - but WITHOUT ANY WARRANTY; without even the implied warranty of
 - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 - GNU Affero General Public License for more details.
 -
 - You should have received a copy of the GNU Affero General Public License
 - along with this program.  If not, see <http://www.gnu.org/licenses/>.
--%>

<%@ page import="org.mskcc.cbio.portal.servlet.*" %>
<%@ page import="org.mskcc.cbio.portal.util.XssRequestWrapper" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="org.apache.commons.lang.*" %>
<%@ page import="org.mskcc.cbio.portal.util.GlobalProperties" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.List" %>

<%
    /*
     *
     * Parse results from Query Builder
     * In cases that the query form is not initialized
     *
     */
    
    org.mskcc.cbio.portal.servlet.ServletXssUtil localXssUtil = ServletXssUtil.getInstance();
    
    // get tab index (query form or data download form)
    String localTabIndex = request.getParameter(QueryBuilder.TAB_INDEX);
    if (localTabIndex == null) {
        localTabIndex = QueryBuilder.TAB_VISUALIZE;
    } else {
        localTabIndex = URLEncoder.encode(localTabIndex);
    }

    // get cancer study
    String localCancerStudyList = (String) request.getParameter(QueryBuilder.CANCER_STUDY_LIST);
    String selectedCancerStudyId = (String) request.getParameter(QueryBuilder.CANCER_STUDY_ID);
    Boolean isVirtualCohort = (Boolean)request.getAttribute(QueryBuilder.IS_VIRTUAL_STUDY);

    // get genetic profile
    HashSet<String> localGeneticProfileIdSet = (HashSet<String>) request.getAttribute(QueryBuilder.GENETIC_PROFILE_IDS);
    
    // get case set / cases
    String localSampleSetId = (String) request.getAttribute(QueryBuilder.CASE_SET_ID);
    String localCaseIds = (String)request.getAttribute(QueryBuilder.CASE_IDS);
    
    // get gene list
    String localGeneList = request.getParameter(QueryBuilder.GENE_LIST);
	if (request instanceof XssRequestWrapper)
	{
		localGeneList = localXssUtil.getCleanInput(
			((XssRequestWrapper)request).getRawParameter(QueryBuilder.GENE_LIST));
	}
    String localGeneSetChoice = request.getParameter(QueryBuilder.GENE_SET_CHOICE);
    
    // get zscore threshold
    String localzScoreThreshold = request.getParameter(QueryBuilder.Z_SCORE_THRESHOLD);
    if (localzScoreThreshold == null) {
        localzScoreThreshold = "2.0";
    }
    String localRppaScoreThreshold = request.getParameter(QueryBuilder.RPPA_SCORE_THRESHOLD);
    if (localRppaScoreThreshold == null) {
        localRppaScoreThreshold = "2.0";
    }
    
    // get client transpost matrix
	String clientTranspose = request.getParameter(QueryBuilder.CLIENT_TRANSPOSE_MATRIX);
    if (localGeneSetChoice == null) {
        localGeneSetChoice = "user-defined-list";
    }
    
    // Get prioritized studies for study selector
    List<String[]> priorityStudies = GlobalProperties.getPriorityStudies();
%>

<script type="text/javascript" src="js/lib/oql/oql-parser.js" charset="utf-8"></script>
<script type="text/javascript">

    // Prioritized studies for study selector
    window.priority_studies = [];
    <% for (String[] group : priorityStudies) {
            if (group.length > 1) {
                    out.println("window.priority_studies.push({'category':'"+group[0]+"',");
                    out.println("'studies':[");
                    int i = 1;
                    while (i < group.length) {
                            if (i >= 2) {
                                    out.println(",");
                            }
                            out.println("'"+group[i]+"'");
                            i++;
                    }
                    out.println("]})");
            }
        } %>
            
    // Store the currently selected options as global variables;
    window.cancer_study_id_selected = '<%= selectedCancerStudyId %>';
    window.cancer_study_list_param = '<%= QueryBuilder.CANCER_STUDY_LIST%>';
    window.cancer_study_list_selected = '<%= localCancerStudyList %>';
    window.case_set_id_selected = '<%= localSampleSetId %>';
    window.is_virtual_cohort = '<%= isVirtualCohort %>';
    var _str = '<%= localCaseIds %>' === 'null'? '': '<%= localCaseIds %>'.trim();
    _str = _str.replace(/\+/g, '\n');
    _str = _str.replace(/\|/g, '\t');
    window.case_ids_selected = _str;
    window.gene_set_id_selected = '<%= localGeneSetChoice %>';
    window.tab_index = '<%= localTabIndex %>';
    window.zscore_threshold = '<%= localzScoreThreshold %>';
    window.rppa_score_threshold = '<%= localRppaScoreThreshold %>';

    //  Store the currently selected genomic profiles within an associative array
    window.genomic_profile_id_selected = new Array();
    <%
        if (localGeneticProfileIdSet != null) {
            for (String geneticProfileId:  localGeneticProfileIdSet) {
                geneticProfileId = localXssUtil.getCleanerInput(geneticProfileId);
                out.println ("window.genomic_profile_id_selected['" + geneticProfileId + "']=1;");
            }
        }
    %>

</script>

<div class="main_query_panel">
    <div id="main_query_form">
        <form id="main_form" name="main_form" action="index.do" method="post">
        <%@ include file="step1_json.jsp" %>
        <%@ include file="step2_json.jsp" %>
        <%@ include file="step3_json.jsp" %>
        <%@ include file="step4_json.jsp" %>
        <%@ include file="step5_json.jsp" %>
        <input type="hidden" id="clinical_param_selection" name="clinical_param_selection"
        	value='<%= request.getParameter("clinical_param_selection") %>'>
        <input type="hidden" id="<%= QueryBuilder.TAB_INDEX %>" name="<%= QueryBuilder.TAB_INDEX %>"
           value="<%= localTabIndex %>">
        <p>
        <% conditionallyOutputTransposeMatrixOption (localTabIndex, clientTranspose, out); %>
        </p>
        <p>
            <input type="button" id="dashboard_button" class="btn btn-default btn-lg" name="Summary" value="Summary" />
            <button id="main_submit" class="btn btn-default btn-lg" name="<%= QueryBuilder.ACTION_NAME%>" value="<%= QueryBuilder.ACTION_SUBMIT %>" title='Submit Query' readonly>Query</button>
            <% conditionallyOutputGenomespaceOption(localTabIndex, out); %>
        </p>
        </form>
    </div>
</div>

<script>
    // work around for bug: using HTML disabling would disable tooltip as well, therefore self-defined disable css / functions
    cbio.util.toggleMainBtn("dashboard_button", "disable");
    cbio.util.toggleMainBtn("main_submit", "disable");

    // fill form 
    
    
</script>

<%!
    private void conditionallyOutputTransposeMatrixOption(String localTabIndex,
            String clientTranspose, JspWriter out)
            throws IOException {
        if (localTabIndex.equals(QueryBuilder.TAB_DOWNLOAD)) {
            outputTransposeMatrixOption(clientTranspose, out);
        }
    }

    private void outputTransposeMatrixOption(String clientTranspose, JspWriter out) throws IOException {
        String checked = hasUserSelectedTheTransposeOption(clientTranspose);
        out.println ("&nbsp;Clicking submit will generate a tab-delimited file "
            + " containing your requested data.");
        out.println ("<div class='checkbox'><label>");
        out.println ("<input id='client_transpose_matrix' type=\"checkbox\" "+ checked + " name=\""
                + QueryBuilder.CLIENT_TRANSPOSE_MATRIX
                + "\"/> <p>Transpose data matrix.</p>");
        out.println ("</label></div>");
    }

    private String hasUserSelectedTheTransposeOption(String clientTranspose) {
        if (clientTranspose != null) {
            return "checked";
        } else {
            return "";
        }
    }

    private void conditionallyOutputGenomespaceOption(String localTabIndex, JspWriter out)
            throws IOException {
        if (GlobalProperties.genomespaceEnabled() && localTabIndex.equals(QueryBuilder.TAB_DOWNLOAD)) {
            out.println("<a id=\"gs_submit\" " +
                        "class=\"ui-button ui-widget ui-state-default ui-corner-all\" " +
                        "style=\"height: 34px;\" " +
                        "title=\"Send data matrix to GenomeSpace.\" " +
                        "href=\"#\" onclick=\"prepGSLaunch($('#main_form'), " +
                        "$('#select_single_study').val(), " +
                        "$('#genomic_profiles'));\"><img src=\"images/send-to-gs.png\" alt=\"Send to GenomeSpace\"/></a>");
        }
    }
%>
