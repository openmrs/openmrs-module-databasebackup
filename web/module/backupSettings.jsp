<%@ include file="/WEB-INF/template/include.jsp"%>

<%@ include file="/WEB-INF/template/header.jsp"%>


<div id="mainTabsSummary">
    <ul id="menu">
        <li class="first">
            <a href="${pageContext.request.contextPath}/admin">Admin</a>
        </li>
        <li id="tab1">
            <a href="${pageContext.request.contextPath}/module/databasebackup/backup.form">Backup Database</a>
        </li>        
        <li class="active" id="tab2">
            <a href="${pageContext.request.contextPath}/module/databasebackup/settings.form">Backup Settings</a>            
        </li>
    </ul>
</div>

<h2><spring:message code="databasebackup.link.settings" /></h2>

<ul>
	<li>Enter the tables to be included into the backup comma separated (i.e.: cohort, concept) into field 'databasebackup.tablesIncluded' or use 'all' (default) to include all.</li>
	<li>Enter the tables to be excluded from the backup comma separated (i.e.: hl7_in_archive, hl7_in_error) into field 'databasebackup.tablesExcluded' or use 'all' (default) to exclude all. If you don't want to exclude any tables, you could also leave it to the default value 'none'.</li>
	<li>Explicitly entered table names overrule any eventual 'all' or 'none' entries in the opposite settings field.</li>
</li>  
<br/>
<openmrs:globalProperty var="databasebackupTables" key="databasebackup.tables" defaultValue="*"/><br/><br/>
<openmrs:portlet id="globalProperty" url="globalProperties" parameters="propertyPrefix=databasebackup.tables" /><br/><br/>

<%@ include file="/WEB-INF/template/footer.jsp"%>
