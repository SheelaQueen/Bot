function pageReady() {
    var prefixInput = $("#prefixInput"), langSelect = $("#dropdownLanguage"), permissions = $(".permDropdown"), permStartGame = $(permissions.get(0)), permStopGame = $(permissions.get(1)), submitButton = $("#submitButton")

    instances = M.FormSelect.init(document.querySelectorAll('select'))
    submitButton.click(() => {
        var oldText = submitButton.text()
        submitButton.text("Saving...").addClass("disabled")

        var values = {
            prefix: prefixInput.val().length == 0 ? undefined : prefixInput.val(),
            language: langSelect.val(),
            permStartGame: permStartGame.val() == "default" ? undefined : permStartGame.val(),
            permStopGame: permStopGame.val() == "default" ? undefined : permStopGame.val()
        }
        apiRequest("", values).then(n => {
            M.toast({ html: "Settings saved." })
            submitButton.removeClass("disabled").text(oldText)
        }).catch(reason => {
            M.toast({ html: "Failed to save settings:<br>" + reason })
            submitButton.removeClass("disabled").text(oldText)
        })
    })
}

function getHeaderProperties() {
    return {
        breadcrumbs: [
            { name: "GamesROB", path: "/" },
            { name: guildName, path: window.location.href.substring(0, window.location.href.length - "edit".length) },
            { name: "Settings", path: window.location.href }
        ], color: "#b71c1c",
        title: guildName + " GamesROB Settings"
    }
}