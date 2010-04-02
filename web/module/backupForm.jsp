<%@ include file="/WEB-INF/template/include.jsp"%>

<openmrs:htmlInclude file="/dwr/engine.js" />
<openmrs:htmlInclude file="/dwr/util.js" />
<openmrs:htmlInclude file="/dwr/interface/BackupFormController.js"/>

<%@ include file="/WEB-INF/template/header.jsp"%>

<script type="text/javascript">
function showProgress() {  
  var filename = '${fileId}';
  BackupFormController.getProgress(filename, function(data) {	    
    if (data!=null && data!='') document.getElementById("progressDisplay").innerHTML = data;
  });
}
</script>

<div id="mainTabsSummary">
    <ul id="menu">
        <li class="first">
            <a href="${pageContext.request.contextPath}/admin">Admin</a>
        </li>
        <li class="active" id="tab1">
            <a href="${pageContext.request.contextPath}/module/databasebackup/backup.form">Backup Database</a>
        </li>        
        <li id="tab2">
            <a href="${pageContext.request.contextPath}/module/databasebackup/settings.form">Backup Settings</a>            
        </li>
    </ul>
</div>

<h2><spring:message code="databasebackup.link.backup" /></h2>

<c:if test="${not empty msg}">
    Progress:<div id="progressDisplay">&nbsp;</div>
    <br/><br/>
    <a href="backup.form">Run another database backup</a>
    
    <script>
    document.getElementById("progressDisplay").innerHTML = "Starting...";    
    // window.setInterval("BackupFormController.getProgress(showProgress)",1*1000);
    window.setInterval("showProgress()",1*1500);
    
    </script>    
</c:if>

<c:if test="${empty msg}">

<openmrs:globalProperty var="backupTablesIncluded" key="databasebackup.tablesIncluded" defaultValue="*"/>
<openmrs:globalProperty var="backupTablesExcluded" key="databasebackup.tablesExcluded" defaultValue="*"/>
Included tables: ${backupTablesIncluded}<br/>
Excluded tables: ${backupTablesExcluded}<br/>
<br/>
 
<form method="post">
    <input type="hidden" id="act" name="act" value="backup">
    <input type="submit" value="Execute database backup now">
</form>

</c:if>

<%@ include file="/WEB-INF/template/footer.jsp"%>
