/*
 Adobe AEM Brightcove Connector

 Copyright (C) 2018 Coresecure Inc.

 Authors:
 Alessandro Bonfatti
 Yan Kisen
 Pablo Kropilnicki

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 - Additional permission under GNU GPL version 3 section 7
 If you modify this Program, or any covered work, by linking or combining
 it with httpclient 4.1.3, httpcore 4.1.4, httpmine 4.1.3, jsoup 1.7.2,
 squeakysand-commons and squeakysand-osgi (or a modified version of those
 libraries), containing parts covered by the terms of APACHE LICENSE 2.0
 or MIT License, the licensors of this Program grant you additional
 permission to convey the resulting work.
*/

document.addEventListener("DOMContentLoaded", function() {

	const account = document.querySelector("[name='./account']");
	const playerID = document.querySelector("[name='./playerID']");

	if (!(playerID) || !(account)) {
		return;
	}

	async function fillPlayers(selectedAccount, selectedPlayer, savedValue) {
		try {
			// Clear existing options & value first
			selectedPlayer.value = "";
			const existing = selectedPlayer.querySelectorAll("coral-select-item");
			existing.forEach(item => item.remove());

			// add a placeholder when list/selection is refreshed
			const placeholder = new Coral.Select.Item();
			placeholder.value = "";
			placeholder.textContent = "Select";
			selectedPlayer.appendChild(placeholder);

			const response = await fetch("/bin/brightcove/api?a=players&account_id=" + encodeURIComponent(selectedAccount));
			if (!response.ok) throw new Error(`HTTP error! Status: ${response.status}`);

			const options = await response.json();
			const responseData = (typeof options === "string" ? JSON.parse(options) : options);

			for (const key in responseData) {
				if (Object.prototype.hasOwnProperty.call(responseData, key)) {
					for (const item in responseData[key]) {
						const opt = new Coral.Select.Item();
						opt.value = responseData[key][item].id;
						opt.textContent = responseData[key][item].name;
						selectedPlayer.appendChild(opt);
					}
				}
			}

			if (savedValue) {
				selectedPlayer.value = savedValue;
			}
		} catch (error) {
			console.error("Error fetching data for", selectedPlayer.name, error);
		}
	}

	account.addEventListener("change", function() {
		const selectedValue = account.value;

		if (selectedValue) {
			fillPlayers(selectedValue, playerID, "");
		}
	});

	var $form = document.querySelector("form");
	$.getJSON($form.getAttribute("action") + ".json").done(function(data) {
		if (!(data)) {
			return;
		}

		var accountSelected = document.querySelector('[name="./account"]').value;
		const savedValue = data.playerID;
		fillPlayers(accountSelected, playerID, savedValue);
	});
});