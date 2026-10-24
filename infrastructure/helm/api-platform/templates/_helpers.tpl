{{- define "ap.fullname" -}}
{{- printf "%s-%s" .root.Release.Name .comp.name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "ap.labels" -}}
app.kubernetes.io/name: {{ .comp.name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/part-of: api-platform
app.kubernetes.io/version: {{ .root.Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version | replace "+" "_" | quote }}
{{- end -}}

{{- define "ap.selector" -}}
app.kubernetes.io/name: {{ .comp.name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end -}}

{{- define "ap.image" -}}
{{- printf "%s:%s" .comp.image.repository (default .root.Chart.AppVersion .comp.image.tag) -}}
{{- end -}}
