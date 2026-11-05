<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head><meta charset="UTF-8"><title>대출 상품 검색</title></head>
<body>
<h1>대출 상품 검색</h1>
<p>검색어: <span>${q}</span></p>
<p>검색어(재표시): <span><%= request.getParameter("q") %></span></p>

<p>검색 결과 요약: <span id="summary"></span></p>
<script>
    const q = new URLSearchParams(location.search).get("q") || "";
    document.getElementById("summary").innerHTML = q + " 에 대한 검색 결과가 없습니다.";
</script>
</body>
</html>
