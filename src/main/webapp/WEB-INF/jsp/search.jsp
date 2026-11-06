<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>

<!DOCTYPE html>
<html lang="ko">
<head><meta charset="UTF-8"><title>대출 상품 검색</title></head>
<body>
<h1>대출 상품 검색</h1>
<p>c:out <span><c:out value="${q}" /></span></p>
<p>fn:escapeXml <span>${fn:escapeXml(q)}</span></p>
<p>textContent <span id="dom-result"></span></p>
<script>
	const params = new URLSearchParams(location.search);
	const q = params.get("q") || "";
	const element = document.getElementById("dom-result");
	
	element.textContent = q;
</script>
</body>
</html>
