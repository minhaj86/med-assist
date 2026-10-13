import { zodResolver } from '@hookform/resolvers/zod'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Tab, Tabs, TextField, Typography } from '@mui/material'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useCreateManualDocument, useUploadDocument, useUploadDocumentVersion } from '../../../api/hooks'
import { ProblemAlert } from '../../../components/ProblemAlert'
import { DocumentMetadataFields } from './DocumentMetadataFields'
import { emptyMetadata, metadataSchema, toMetadataInput, type MetadataFormValues } from './documentMetadata'

const ACCEPT = '.pdf,.txt,.md,.markdown,application/pdf,text/plain,text/markdown'

/** Upload a file (PDF/TXT/Markdown), enter text manually, or upload a new version of an existing document. */
export function UploadDocumentDialog({ open, onClose, newVersionOf, initial }: {
  open: boolean; onClose: () => void; newVersionOf?: string; initial?: Partial<MetadataFormValues>
}) {
  const [mode, setMode] = useState<'file' | 'manual'>('file')
  const [file, setFile] = useState<File | null>(null)
  const [text, setText] = useState('')
  const upload = useUploadDocument()
  const uploadVersion = useUploadDocumentVersion()
  const manual = useCreateManualDocument()
  const { register, control, handleSubmit, formState: { errors } } = useForm<MetadataFormValues>({
    resolver: zodResolver(metadataSchema), defaultValues: { ...emptyMetadata, ...initial },
  })
  const pending = upload.isPending || uploadVersion.isPending || manual.isPending
  const error = upload.error ?? uploadVersion.error ?? manual.error
  const submit = handleSubmit((values) => {
    const metadata = toMetadataInput(values)
    if (mode === 'manual' && !newVersionOf) {
      manual.mutate({ metadata, text }, { onSuccess: onClose })
    } else if (file) {
      if (newVersionOf) uploadVersion.mutate({ documentId: newVersionOf, metadata, file }, { onSuccess: onClose })
      else upload.mutate({ metadata, file }, { onSuccess: onClose })
    }
  })
  const contentReady = mode === 'manual' && !newVersionOf ? text.trim().length > 0 : !!file
  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{newVersionOf ? 'Upload new document version' : 'Add knowledge document'}</DialogTitle>
      <DialogContent>
        <Alert severity="warning" sx={{ mb: 1 }}>
          Upload only approved clinical content you are licensed to use. Never upload patient-identifiable information.
        </Alert>
        {!newVersionOf && (
          <Tabs value={mode} onChange={(_, v: 'file' | 'manual') => setMode(v)} sx={{ mb: 1 }}>
            <Tab value="file" label="Upload file" /><Tab value="manual" label="Enter text manually" />
          </Tabs>
        )}
        <ProblemAlert error={error} title="Document not saved" />
        <DocumentMetadataFields register={register} control={control} errors={errors} />
        <Box sx={{ mt: 2 }}>
          {mode === 'file' || newVersionOf ? (
            <Box>
              <Button component="label" variant="outlined" startIcon={<UploadFileIcon />}>
                Choose file
                <input hidden type="file" accept={ACCEPT} aria-label="Document file" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
              </Button>
              <Typography variant="body2" component="span" sx={{ ml: 1 }}>
                {file ? `${file.name} (${Math.ceil(file.size / 1024)} KB)` : 'PDF, TXT or Markdown'}
              </Typography>
            </Box>
          ) : (
            <TextField label="Document text" multiline minRows={10} value={text} onChange={(e) => setText(e.target.value)}
                       helperText="Markdown headings (#) are used for section detection" slotProps={{ htmlInput: { maxLength: 500000 } }} />
          )}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={() => void submit()} disabled={pending || !contentReady}>
          {pending ? 'Uploading…' : newVersionOf ? 'Upload version' : 'Add document'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
