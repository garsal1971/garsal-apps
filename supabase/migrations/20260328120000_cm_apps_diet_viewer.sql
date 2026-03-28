INSERT INTO cm_apps (title, description, html_file, active, score_query)
VALUES (
  'Diet Viewer',
  'Visualizza il piano dieta settimanale generato da Claude',
  'diet-viewer.html',
  true,
  'SELECT 0'
)
ON CONFLICT DO NOTHING;
