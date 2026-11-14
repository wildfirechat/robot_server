(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var form = document.getElementById("message-filter");
        if (!form) {
            return;
        }
        var typeSelect = form.querySelector("[data-conv-type]");
        var targetInput = form.querySelector("[data-target]");
        var selectedTarget = form.querySelector("[data-selected-target]");
        var modal = document.querySelector("[data-search-modal]");
        var modalTitle = modal ? modal.querySelector("[data-modal-title]") : null;
        var userSection = modal ? modal.querySelector("[data-user-search]") : null;
        var groupSection = modal ? modal.querySelector("[data-group-search]") : null;
        var userField = modal ? modal.querySelector("[data-user-field]") : null;
        var userKeyword = modal ? modal.querySelector("[data-user-keyword]") : null;
        var userResults = modal ? modal.querySelector("[data-user-results]") : null;
        var groupKeyword = modal ? modal.querySelector("[data-group-keyword]") : null;
        var groupResults = modal ? modal.querySelector("[data-group-results]") : null;

        function submitFilter() {
            form.submit();
        }

        if (typeSelect) {
            typeSelect.addEventListener("change", function () {
                targetInput.value = "";
                selectedTarget.textContent = "未选择";
                submitFilter();
            });
        }

        function openModal(mode) {
            if (!modal) {
                return;
            }
            modal.hidden = false;
            if (mode === "group") {
                modalTitle.textContent = "搜索群组";
                userSection.hidden = true;
                groupSection.hidden = false;
                groupResults.textContent = "";
                groupKeyword.value = "";
                groupKeyword.focus();
            } else {
                modalTitle.textContent = "搜索用户";
                userSection.hidden = false;
                groupSection.hidden = true;
                userResults.textContent = "";
                userKeyword.value = "";
                userKeyword.focus();
            }
        }

        function closeModal() {
            if (modal) {
                modal.hidden = true;
            }
        }

        function selectTarget(id, name) {
            targetInput.value = id;
            selectedTarget.textContent = name;
            closeModal();
            submitFilter();
        }

        function showMessage(container, text) {
            container.textContent = "";
            var div = document.createElement("div");
            div.className = "help";
            div.textContent = text;
            container.appendChild(div);
        }

        function renderResult(container, item, meta) {
            var button = document.createElement("button");
            button.type = "button";
            button.className = "search-result";

            var name = document.createElement("span");
            name.className = "search-result-name";
            name.textContent = item.name;
            button.appendChild(name);

            if (meta) {
                var detail = document.createElement("span");
                detail.className = "search-result-meta";
                detail.textContent = meta;
                button.appendChild(detail);
            }

            button.addEventListener("click", function () {
                selectTarget(item.id, item.name);
            });
            container.appendChild(button);
        }

        function searchUsers() {
            var field = userField.value;
            var keyword = userKeyword.value.trim();
            if (!keyword) {
                showMessage(userResults, "请输入搜索关键词");
                return;
            }
            userResults.textContent = "";
            showMessage(userResults, "搜索中…");
            fetch("/admin/messages/users/search?field=" + encodeURIComponent(field)
                + "&keyword=" + encodeURIComponent(keyword), {credentials: "same-origin"})
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("搜索失败");
                    }
                    return response.json();
                })
                .then(function (data) {
                    userResults.textContent = "";
                    var results = data.results || [];
                    if (results.length === 0) {
                        showMessage(userResults, "没有找到匹配的用户");
                        return;
                    }
                    results.forEach(function (item) {
                        var meta = [item.mobile, item.email].filter(Boolean).join(" · ");
                        renderResult(userResults, item, meta);
                    });
                })
                .catch(function () {
                    showMessage(userResults, "搜索失败，请稍后重试");
                });
        }

        function searchGroups() {
            var keyword = groupKeyword.value.trim();
            if (!keyword) {
                showMessage(groupResults, "请输入群名称");
                return;
            }
            groupResults.textContent = "";
            showMessage(groupResults, "搜索中…");
            fetch("/admin/messages/groups/search?keyword=" + encodeURIComponent(keyword),
                {credentials: "same-origin"})
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("搜索失败");
                    }
                    return response.json();
                })
                .then(function (data) {
                    groupResults.textContent = "";
                    var results = data.results || [];
                    if (results.length === 0) {
                        showMessage(groupResults, "没有找到匹配的群组");
                        return;
                    }
                    results.forEach(function (item) {
                        renderResult(groupResults, item, "");
                    });
                })
                .catch(function () {
                    showMessage(groupResults, "搜索失败，请稍后重试");
                });
        }

        var openUserButton = document.querySelector("[data-open-user-search]");
        if (openUserButton) {
            openUserButton.addEventListener("click", function () {
                openModal("user");
            });
        }
        var openGroupButton = document.querySelector("[data-open-group-search]");
        if (openGroupButton) {
            openGroupButton.addEventListener("click", function () {
                openModal("group");
            });
        }
        var userSearchButton = modal ? modal.querySelector("[data-user-search-button]") : null;
        if (userSearchButton) {
            userSearchButton.addEventListener("click", searchUsers);
        }
        var groupSearchButton = modal ? modal.querySelector("[data-group-search-button]") : null;
        if (groupSearchButton) {
            groupSearchButton.addEventListener("click", searchGroups);
        }
        if (userKeyword) {
            userKeyword.addEventListener("keydown", function (event) {
                if (event.key === "Enter") {
                    event.preventDefault();
                    searchUsers();
                }
            });
        }
        if (groupKeyword) {
            groupKeyword.addEventListener("keydown", function (event) {
                if (event.key === "Enter") {
                    event.preventDefault();
                    searchGroups();
                }
            });
        }

        document.querySelectorAll("[data-close-modal]").forEach(function (element) {
            element.addEventListener("click", closeModal);
        });
        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape") {
                closeModal();
            }
        });

        document.querySelectorAll("[data-conv-type][data-conv-target]").forEach(function (link) {
            if (!link.classList.contains("conversation-link")) {
                return;
            }
            link.addEventListener("click", function (event) {
                if (!window.confirm("是否查看此会话消息？")) {
                    event.preventDefault();
                }
            });
        });
    });
}());
