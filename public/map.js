// Leaflet - Carte des installations TC11
if(typeof L !== 'undefined' && document.getElementById('map')) {
  // Charger les installations depuis le fichier JSON
  fetch('/installations.json')
    .then(res => res.json())
    .then(installations => {
      // Calculer le centre de toutes les installations
      const latSum = installations.reduce((sum, i) => sum + i.coords[0], 0);
      const lngSum = installations.reduce((sum, i) => sum + i.coords[1], 0);
      const center = [latSum / installations.length, lngSum / installations.length];

      const map = L.map('map').setView(center, 13);
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: '&copy; OpenStreetMap' }).addTo(map);

      // Ajouter un marqueur pour chaque installation
      const markers = installations.map(installation => {
        const popupContent = `
          <strong>${installation.name}</strong><br>
          ${installation.terrains} terrain${installation.terrains > 1 ? 's' : ''}<br>
          Revêtement : ${installation.surface}<br>
          <a href="${installation.url}" target="_blank" rel="noopener">Plus d'infos →</a>
        `;
        return L.marker(installation.coords).addTo(map).bindPopup(popupContent);
      });

      // Ajuster la vue pour afficher tous les marqueurs
      if (markers.length > 0) {
        const group = L.featureGroup(markers);
        map.fitBounds(group.getBounds().pad(0.1));
      }
    });
}
