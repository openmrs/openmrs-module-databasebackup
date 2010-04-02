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

<openmrs:portlet id="globalProperty" url="globalProperties" parameters="propertyPrefix=databasebackup." /><br/><br/>

<%@ include file="/WEB-INF/template/footer.jsp"%>
